-- 2026-09-26 · 房间状态 / 预约时间 / 房主实力
--
-- 用途：给**已经在跑**的库补列（`schema.sql` 只在 Postgres 首次初始化时执行）。
-- 跑法（在 /opt/dartvio 下）：
--   sudo docker compose cp ./deploy/migrations/2026-09-26_room_status.sql db:/tmp/patch.sql
--   sudo docker compose exec -T db psql -U postgres -d postgres -f /tmp/patch.sql
--
-- 说明：全部用 add column if not exists，重复执行安全；不加 not null 约束以外的
-- 强校验，免得老数据挡住迁移。

alter table public.dartvio_rooms
    add column if not exists status      text    not null default 'WAITING';
alter table public.dartvio_rooms
    add column if not exists starts_at   bigint;
alter table public.dartvio_rooms
    add column if not exists host_ppr    numeric;
alter table public.dartvio_rooms
    add column if not exists host_credit integer;

-- 迁移时刻已存在的房间一律按 ENDED 归档：它们是这一列出现之前建的，
-- 到底打没打完已经无从判断，而把它们留在 WAITING 里就是留下「可以再点进去」的入口。
--
-- 注意别写成 `where status not in ('WAITING','PLAYING','ENDED')`：
-- 上面的 add column 带 default 'WAITING'，所有老行都被填成 'WAITING'，
-- 那个条件恒为假，一句都改不动（2026-09-26 首次执行就是 UPDATE 0）。
-- 判据必须是「建库早于此刻」，也就是下面这行。
update public.dartvio_rooms
   set status = 'ENDED'
 where created_at < (extract(epoch from now()) * 1000)::bigint;

-- PostgREST 会缓存 schema：不 reload 的话新列一律按「不存在」报错。
notify pgrst, 'reload schema';
