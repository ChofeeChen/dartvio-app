#!/bin/bash
# DartVio 联机后端 · 一键部署脚本（Ubuntu 22.04，在服务器上以 root 执行）
#
# 做的事：
#   1. 装 Docker（已装则跳过）
#   2. 在 /opt/dartvio 写入 compose / 建表 SQL / Caddy 配置 / JWT 签发脚本
#      （密码与 JWT 密钥自动随机生成，不用手输）
#   3. 启动 Postgres + PostgREST + Caddy（自动申请 Let's Encrypt 证书）
#   4. 自测并输出 ANON_KEY（给 App 用，最后一行）
set -euo pipefail

DOMAIN="43.156.5.140.nip.io"

dc() { if docker compose version >/dev/null 2>&1; then docker compose "$@"; else docker-compose "$@"; fi; }

echo "== [1/6] 安装 Docker =="
if ! command -v docker >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y docker.io
fi
if ! dc version >/dev/null 2>&1; then
    apt-get install -y docker-compose-v2 2>/dev/null || apt-get install -y docker-compose-plugin 2>/dev/null || apt-get install -y docker-compose
fi
systemctl enable --now docker >/dev/null 2>&1 || true
docker version --format 'docker OK: {{.Server.Version}}'

echo "== [2/6] 写入部署文件 =="
mkdir -p /opt/dartvio && cd /opt/dartvio

DB_PASS=$(openssl rand -hex 12)
AUTH_PASS=$(openssl rand -hex 12)
JWT_SECRET=$(openssl rand -hex 32)

cat > docker-compose.yml <<'YAML'
services:
  db:
    image: postgres:15-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-dartvio}
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./schema.sql:/docker-entrypoint-initdb.d/10-schema.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-postgres}"]
      interval: 5s
      timeout: 3s
      retries: 20

  postgrest:
    image: postgrest/postgrest:v12.2.3
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      PGRST_DB_URI: postgres://authenticator:${AUTHENTICATOR_PASSWORD}@db:5432/${POSTGRES_DB:-dartvio}
      PGRST_DB_SCHEMAS: public
      PGRST_DB_ANON_ROLE: anon
      PGRST_JWT_SECRET: ${PGRST_JWT_SECRET}
      PGRST_DB_MAX_ROWS: "1000"
      PGRST_OPENAPI_SERVER_PROXY_URI: https://${DOMAIN}
    expose:
      - "3000"

  caddy:
    image: caddy:2
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    environment:
      DOMAIN: ${DOMAIN}
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - postgrest

volumes:
  pgdata: {}
  caddy_data: {}
  caddy_config: {}
YAML

cat > schema.sql <<'SQL'
create table if not exists public.dartvio_rooms (
    id               text primary key,
    name             text        not null default '',
    creator_id       text        not null default '',
    config           jsonb       not null default '{}'::jsonb,
    visibility       text        not null default 'PUBLIC',
    allow_spectators boolean     not null default true,
    created_at       bigint      not null default (extract(epoch from now()) * 1000)::bigint,
    join_policy      text,
    host_name        text,
    host_avatar      text,
    expires_at       bigint
);

create table if not exists public.dartvio_room_events (
    id         bigserial primary key,
    room_id    text    not null,
    seq        integer not null,
    actor_id   text    not null default '',
    type       text    not null default '',
    payload    jsonb   not null default '{}'::jsonb,
    created_at bigint  not null default (extract(epoch from now()) * 1000)::bigint,
    constraint dartvio_room_events_room_seq_key unique (room_id, seq)
);

create table if not exists public.dartvio_room_state (
    id         bigserial primary key,
    room_id    text        not null,
    seq        integer     not null default 0,
    state      jsonb       not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists dartvio_rooms_created_at_idx
    on public.dartvio_rooms (created_at desc);
create index if not exists dartvio_room_events_room_seq_idx
    on public.dartvio_room_events (room_id, seq);
create index if not exists dartvio_room_state_room_seq_idx
    on public.dartvio_room_state (room_id, seq desc);

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin noinherit;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticator') then
        create role authenticator noinherit login password 'CHANGE_ME';
    end if;
end
$$;

grant anon to authenticator;
grant usage on schema public to anon;
grant select, insert, update, delete on all tables in schema public to anon;
grant usage, select on all sequences in schema public to anon;
alter default privileges in schema public
    grant select, insert, update, delete on tables to anon;

notify pgrst, 'reload schema';
SQL
# authenticator 的登录密码由脚本随机生成，写进建表 SQL（只在首次建库时执行）
sed -i "s/CHANGE_ME/${AUTH_PASS}/" schema.sql

cat > Caddyfile <<'CADDY'
{$DOMAIN} {
	handle_path /rest/v1/* {
		reverse_proxy postgrest:3000
	}

	handle /realtime/* {
		respond "realtime not enabled" 404
	}

	handle {
		respond "dartvio api" 200
	}
}
CADDY

cat > gen_anon_jwt.py <<'PY'
import base64, hashlib, hmac, json, sys

DEFAULT_EXP = 2080000000

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("usage: gen_anon_jwt.py <secret> [exp]")
    secret = sys.argv[1].strip()
    exp = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_EXP
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {"role": "anon", "iss": "dartvio", "iat": 1760000000, "exp": exp}
    signing_input = (
        b64url(json.dumps(header, separators=(",", ":")).encode())
        + "." + b64url(json.dumps(payload, separators=(",", ":")).encode())
    )
    sig = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    print(signing_input + "." + b64url(sig))

if __name__ == "__main__":
    main()
PY

cat > .env <<ENV
DOMAIN=${DOMAIN}
POSTGRES_DB=dartvio
POSTGRES_USER=postgres
POSTGRES_PASSWORD=${DB_PASS}
AUTHENTICATOR_PASSWORD=${AUTH_PASS}
PGRST_JWT_SECRET=${JWT_SECRET}
ENV
chmod 600 .env

echo "== [3/6] 启动容器 =="
dc up -d
sleep 8
# 建表 SQL 由 Postgres 首次初始化执行，可能晚于 PostgREST 首次启动，重启一次确保加载到表
dc restart postgrest >/dev/null
sleep 5

echo "== [4/6] 自测（服务器本机走 HTTPS） =="
TOKEN=$(python3 gen_anon_jwt.py "${JWT_SECRET}")
echo -n "GET /rest/v1/dartvio_rooms -> "
curl -s --max-time 20 "https://${DOMAIN}/rest/v1/dartvio_rooms?select=id&limit=1" \
    -H "apikey: ${TOKEN}" -H "Authorization: Bearer ${TOKEN}" || echo "(curl 失败)"
echo ""

echo "== [5/6] 容器状态 =="
dc ps

echo ""
echo "== [6/6] 完成。把下面整行（ANON_KEY=...）复制发回 =="
echo "ANON_KEY=${TOKEN}"
