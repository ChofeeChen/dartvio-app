-- DartVio 联机后端 · 建表与授权
-- 表结构与 Supabase 上的三张表一致，客户端零改动即可切换过来。
-- 由 Postgres 容器在首次初始化时自动执行（/docker-entrypoint-initdb.d）。

create table if not exists public.dartvio_rooms (
    id               text primary key,
    name             text        not null default '',
    creator_id       text        not null default '',
    config           jsonb       not null default '{}'::jsonb,
    visibility       text        not null default 'PUBLIC',
    allow_spectators boolean     not null default true,
    -- 大厅按 created_at desc 取前 N 条；App 只用它排序，不读值
    created_at       bigint      not null default (extract(epoch from now()) * 1000)::bigint,
    join_policy      text,
    host_name        text,
    host_avatar      text,
    -- 等待房的到期时刻（epoch 毫秒）；null = 不计时
    expires_at       bigint,
    -- 房间状态：WAITING / PLAYING / ENDED。
    -- 大厅索引行本身不带事件，而「这间房是不是已经打完了」是**点进去之前**就要回答的
    -- 问题：索引行永远停在 WAITING 时，两台手机都能再点进去各补一条加入事件，
    -- 同一份事件流于是在两端重放出不同的比分与回合（2026-09-26 真机实测）。
    -- 老库没有这一列时客户端按 WAITING 处理，功能降级但列表照常拉得出来。
    status           text        not null default 'WAITING',
    -- 预约开始时刻（epoch 毫秒）；null = 建好就开打。
    starts_at        bigint,
    -- 房主的 PPR 与信用分：建房后补写一次，让陌生人在点进来之前对房主有个预期。
    host_ppr         numeric,
    host_credit      integer
);

create table if not exists public.dartvio_room_events (
    id         bigserial primary key,
    room_id    text    not null,
    seq        integer not null,
    actor_id   text    not null default '',
    type       text    not null default '',
    payload    jsonb   not null default '{}'::jsonb,
    created_at bigint  not null default (extract(epoch from now()) * 1000)::bigint,
    -- 并发投镖的判定依据：同一个 (room_id, seq) 只能有一条。
    -- 撞上这条约束时 PostgREST 回 409，客户端据此重拉事件、换 seq 重投。
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

-- ===== 角色与授权 =====
-- anon：PostgREST 处理带 JWT 的请求时切到的角色（客户端 JWT 里写着 "role":"anon"）
-- authenticator：PostgREST 自己用来连库的登录角色，只负责连接，不持任何数据权限
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin noinherit;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticator') then
        -- 密码由 compose 用 AUTHENTICATOR_PASSWORD 覆写，这里的默认值只是兜底
        create role authenticator noinherit login password 'CHANGE_ME';
    end if;
end
$$;

grant anon to authenticator;

grant usage on schema public to anon;
grant select, insert, update, delete on all tables in schema public to anon;
grant usage, select on all sequences in schema public to anon;

-- 之后新建的表也自动给 anon，省得以后加表忘了授权
alter default privileges in schema public
    grant select, insert, update, delete on tables to anon;

-- 让 PostgREST 重新加载 schema（建表发生在它启动之后的情况）
notify pgrst, 'reload schema';
