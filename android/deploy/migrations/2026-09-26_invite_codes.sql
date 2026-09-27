-- ---------------------------------------------------------------------------
-- Beta 邀请码后台校验（2026-09-26）
--
-- 为什么要有这张表：包里那份白名单只能回答「这串字符有没有发过」，
-- 回答不了三件要紧的事 —— ① 这个码是不是**已经在别的手机上启用**过；
-- ② 这个码是不是已经**被回收**；③ 谁激活了哪个码（发放台账要对得上实际人数）。
--
-- 审计口径：**发放规则必须在服务端**。写在客户端里的规则可以被改包绕过，
-- 那样的校验只是装饰 —— 所以这里是 RPC（security definer），客户端只能问，不能改。
-- ---------------------------------------------------------------------------

create table if not exists public.dartvio_invites (
    code          text primary key,
    -- ISSUED 已发放未启用 / REVOKED 已回收
    status        text not null default 'ISSUED',
    -- 发放人自己看的备注（渠道、微信群、给谁），**不是**给用户填的字段
    note          text,
    created_at    timestamptz not null default now(),
    activated_at  timestamptz,
    -- 客户端每次安装随机生成的一串 UUID（卸载重装就变），用于「一码是否已在别处启用」
    install_id    text
);

-- 同一个安装 ID 只能占一个码：防止一个人反复换码把渠道统计灌水。
create unique index if not exists dartvio_invites_install_unique
    on public.dartvio_invites (install_id)
    where install_id is not null;

comment on table public.dartvio_invites is
    'Beta 邀请码发放台账：code 是发给人的码，install_id 是启用它的那一台设备。';

-- ---------------------------------------------------------------------------
-- 校验入口：客户端 POST /rest/v1/rpc/redeem_invite
-- 入参 p_code / p_install；返回 { ok, reason?, rank? }
--   ok=true             放行
--   reason=NOT_FOUND    码不在台账里
--   reason=REVOKED      码已回收
--   reason=USED_ELSEWHERE 码已在别的设备启用
-- ---------------------------------------------------------------------------
create or replace function public.redeem_invite(p_code text, p_install text)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    v_row public.dartvio_invites;
    v_rank integer;
begin
    if p_code is null or p_install is null or btrim(p_install) = '' then
        return json_build_object('ok', false, 'reason', 'BAD_REQUEST');
    end if;

    select * into v_row
      from public.dartvio_invites
     where upper(btrim(code)) = upper(btrim(p_code));

    if not found then
        return json_build_object('ok', false, 'reason', 'NOT_FOUND');
    end if;

    if v_row.status = 'REVOKED' then
        return json_build_object('ok', false, 'reason', 'REVOKED');
    end if;

    -- 同一台设备重复提交同一码：幂等放行（用户在弱网下会连点两次）。
    -- 换成了另一台设备：明确拒绝。
    if v_row.install_id is not null and v_row.install_id <> btrim(p_install) then
        return json_build_object('ok', false, 'reason', 'USED_ELSEWHERE');
    end if;

    update public.dartvio_invites
       set activated_at = coalesce(activated_at, now()),
           install_id   = btrim(p_install)
     where code = v_row.code;

    -- 「第几位激活者」：供**运营台账**使用，客户端刻意不展示（会把内测变成排队游戏）。
    select count(*) into v_rank
      from public.dartvio_invites
     where activated_at is not null;

    return json_build_object('ok', true, 'rank', v_rank);
end;
$$;

-- anon 角色要能调用；表本身的写权限不给 anon（全部经由上面的 definer 函数）。
revoke execute on function public.redeem_invite(text, text) from public;
grant  execute on function public.redeem_invite(text, text) to anon, authenticated;

notify pgrst, 'reload schema';
