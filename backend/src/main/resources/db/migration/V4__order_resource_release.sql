-- Resource-release markers make cancellation and rejection idempotent.
-- Existing orders already consumed their stock; future terminal transitions release it once.
alter table orders add column stock_restored boolean not null default false;
alter table orders add column coupon_released boolean not null default false;
