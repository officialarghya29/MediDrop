-- M1 safety invariants: verified pharmacist identity, handover OTP reveal,
-- controlled-medicine classification, sale blocks, and database-level price/stock checks.

alter table pharmacies add column pharmacist_name varchar(160);
alter table pharmacies add column pharmacist_registration_number varchar(80);
alter table pharmacies add column pharmacist_council varchar(160);
alter table pharmacies add column pharmacist_verified boolean not null default false;
alter table pharmacies add column pharmacist_verified_at timestamp(6) with time zone;
alter table pharmacies add column pharmacist_verified_by bigint;

alter table prescriptions add column reviewed_by_registration varchar(80);
alter table prescription_reviews add column pharmacist_registration_number varchar(80);
alter table orders add column delivery_otp varchar(8);

alter table medicines add column sales_blocked boolean not null default false;
update medicines set schedule_class = upper(trim(schedule_class));
update medicines set schedule_class = 'UNKNOWN'
where schedule_class is null or schedule_class not in ('OTC', 'H', 'H1', 'X', 'UNKNOWN');
update medicines set prescription_required = true
where schedule_class in ('H', 'H1', 'X', 'UNKNOWN');

alter table medicines add constraint ck_medicine_schedule
    check (schedule_class in ('OTC', 'H', 'H1', 'X', 'UNKNOWN'));

alter table pharmacy_inventory add constraint ck_inventory_price
    check (price > 0 and mrp > 0 and price <= mrp);
alter table pharmacy_inventory add constraint ck_inventory_stock
    check (stock_quantity >= 0 and reserved_quantity >= 0 and stock_quantity >= reserved_quantity);

alter table order_status_history drop constraint if exists order_status_history_actor_role_check;
alter table order_status_history add constraint order_status_history_actor_role_check
    check (actor_role in ('CUSTOMER', 'PHARMACY', 'DELIVERY', 'ADMIN'));
