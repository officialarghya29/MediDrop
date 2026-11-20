-- Order lifecycle writes are guarded by an optimistic version. If two actors try to
-- move the same order at once, one commit wins and the other receives a recoverable
-- 409 instead of silently overwriting status, stock restoration or coupon release.
alter table orders add column version bigint not null default 0;
