#!/usr/bin/env python3
"""Runs the 50-way single-unit stock race against a live MediDrop API.

This is deliberately an external black-box gate: it seeds 50 verified customers and
carts directly in PostgreSQL, then fires 50 simultaneous COD checkouts through the
real HTTP API. A passing run must produce exactly 10 orders, no negative stock and
no server errors.

Requires: PyJWT, psql on PATH, a seeded demo pharmacy, and a disposable database.
"""

import argparse
import concurrent.futures
import datetime as dt
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

import jwt

RUN = uuid.uuid4().hex[:8]
CUSTOMERS = 50
STOCK = 10
EMAIL_PREFIX = f"stockrace-{RUN}-"


def psql(args, sql):
    env = dict(os.environ, PGPASSWORD=args.pg_password)
    if args.psql_container:
        command = [
            "docker", "exec", "-i", "-e", f"PGPASSWORD={args.pg_password}",
            args.psql_container, "psql", "-U", args.pg_user, "-d", args.pg_db,
            "-v", "ON_ERROR_STOP=1", "-At", "-c", sql,
        ]
    else:
        command = [
            "psql", "-h", args.pg_host, "-p", str(args.pg_port), "-U", args.pg_user,
            "-d", args.pg_db, "-v", "ON_ERROR_STOP=1", "-At", "-c", sql,
        ]
    result = subprocess.run(command, env=env, text=True, capture_output=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


def pick_inventory(args):
    rows = json.loads(psql(args, """
        select json_agg(row_to_json(x)) from (
          select pi.id as inventory_id, pi.medicine_id, pi.pharmacy_id, pi.price
          from pharmacy_inventory pi
          join medicines m on m.id = pi.medicine_id
          join pharmacies p on p.id = pi.pharmacy_id
          where p.status = 'APPROVED' and p.active and p.accepting_orders
            and pi.active and not m.prescription_required
          order by pi.stock_quantity desc, pi.id
          limit 1
        ) x;
    """))
    if not rows:
        raise RuntimeError("No sellable non-prescription inventory found; enable demo seed data")
    return rows[0]


def cleanup_previous_runs(args):
    psql(args, f"""
        begin;
        delete from cart_items where cart_id in (
          select c.id from shopping_carts c join users u on u.id = c.customer_id
          where u.email like 'stockrace-%');
        delete from shopping_carts where customer_id in
          (select id from users where email like 'stockrace-%');
        delete from order_items where order_id in (
          select o.id from orders o join users u on u.id = o.customer_id
          where u.email like 'stockrace-%');
        delete from order_status_history where order_id in (
          select o.id from orders o join users u on u.id = o.customer_id
          where u.email like 'stockrace-%');
        delete from notifications where user_id in
          (select id from users where email like 'stockrace-%');
        delete from audit_logs where actor_user_id in
          (select id from users where email like 'stockrace-%');
        delete from orders where customer_id in
          (select id from users where email like 'stockrace-%');
        delete from addresses where user_id in
          (select id from users where email like 'stockrace-%');
        delete from users where email like 'stockrace-%';
        commit;
    """)


def seed(args, inventory):
    inv = inventory["inventory_id"]
    medicine = inventory["medicine_id"]
    pharmacy = inventory["pharmacy_id"]
    price = inventory["price"]
    psql(args, f"""
        begin;
        update pharmacy_inventory
        set stock_quantity = {STOCK}, reserved_quantity = 0, active = true, version = version + 1
        where id = {inv};

        insert into users
          (full_name, email, phone, password_hash, role, enabled, email_verified,
           phone_verified, locked, created_at, updated_at)
        select 'Stock Race ' || g,
               '{EMAIL_PREFIX}' || g || '@medidrop.test',
               '9' || lpad((930000000 + g)::text, 9, '0'),
               'not-used-by-stock-race', 'CUSTOMER', true, true, true, false, now(), now()
        from generate_series(1, {CUSTOMERS}) g;

        insert into addresses
          (user_id, label, recipient_name, phone, line1, city, state, pincode,
           latitude, longitude, is_default, created_at, updated_at)
        select u.id, 'Race', 'Stock Race ' || u.id, u.phone, '1 Race Lane',
               'Kolkata', 'West Bengal', '700091', 22.5726, 88.3639, true, now(), now()
        from users u where u.email like '{EMAIL_PREFIX}%';

        insert into shopping_carts (customer_id, created_at, updated_at)
        select id, now(), now() from users where email like '{EMAIL_PREFIX}%';

        insert into cart_items
          (cart_id, inventory_id, medicine_id, pharmacy_id, quantity,
           unit_price_snapshot, created_at, updated_at)
        select c.id, {inv}, {medicine}, {pharmacy}, 1, {price}, now(), now()
        from shopping_carts c
        join users u on u.id = c.customer_id
        where u.email like '{EMAIL_PREFIX}%';
        commit;
    """)
    return json.loads(psql(args, f"""
        select json_agg(row_to_json(x)) from (
          select u.id as customer_id, a.id as address_id, u.email
          from users u
          join addresses a on a.user_id = u.id
          where u.email like '{EMAIL_PREFIX}%'
          order by u.id
        ) x;
    """))


def token(secret, row):
    now = dt.datetime.now(dt.timezone.utc)
    return jwt.encode({
        "sub": str(row["customer_id"]),
        "iss": "medidrop",
        "email": row["email"],
        "role": "CUSTOMER",
        "type": "access",
        "iat": now,
        "exp": now + dt.timedelta(minutes=10),
    }, secret, algorithm="HS256")


def place_order(base_url, bearer, row):
    payload = json.dumps({"addressId": row["address_id"], "paymentMethod": "COD"}).encode()
    request = urllib.request.Request(
        base_url.rstrip("/") + "/api/v1/orders",
        data=payload,
        method="POST",
        headers={
            "Authorization": "Bearer " + bearer,
            "Content-Type": "application/json",
            "X-Idempotency-Key": f"stockrace-{RUN}-{row['customer_id']}",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()
    except Exception as error:  # noqa: BLE001 - surface transport failures in the gate
        return 0, repr(error)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--jwt-secret", required=True)
    parser.add_argument("--pg-host", default="127.0.0.1")
    parser.add_argument("--pg-port", type=int, default=5432)
    parser.add_argument("--pg-user", default="medidrop")
    parser.add_argument("--pg-password", default="medidrop")
    parser.add_argument("--pg-db", default="medidrop")
    parser.add_argument("--psql-container", help="Run psql through this Docker container instead of the host")
    args = parser.parse_args()

    cleanup_previous_runs(args)
    inventory = pick_inventory(args)
    rows = seed(args, inventory)
    if len(rows) != CUSTOMERS:
        raise RuntimeError(f"Expected {CUSTOMERS} seeded customers, got {len(rows)}")

    tokens = [token(args.jwt_secret, row) for row in rows]
    with concurrent.futures.ThreadPoolExecutor(max_workers=CUSTOMERS) as pool:
        futures = [pool.submit(place_order, args.base_url, tokens[index], row)
                   for index, row in enumerate(rows)]
        results = [future.result() for future in futures]

    successes = sum(1 for status, _ in results if status == 200)
    conflicts = sum(1 for status, _ in results if status == 409)
    failures = [(status, body[:300]) for status, body in results if status not in (200, 409)]
    stock = int(psql(args, f"select stock_quantity from pharmacy_inventory where id = {inventory['inventory_id']};"))
    orders = int(psql(args, f"""
        select count(*) from orders o join users u on u.id = o.customer_id
        where u.email like '{EMAIL_PREFIX}%';
    """))
    sold = int(psql(args, f"""
        select coalesce(sum(-quantity_delta), 0) from inventory_transactions
        where inventory_id = {inventory['inventory_id']}
          and type = 'SALE' and reason like 'Order MD%'
          and created_at > now() - interval '5 minutes';
    """))

    print(json.dumps({
        "customers": CUSTOMERS,
        "initial_stock": STOCK,
        "http_200": successes,
        "http_409": conflicts,
        "other_responses": failures[:5],
        "orders_created": orders,
        "stock_remaining": stock,
        "units_sold": sold,
    }, indent=2))

    passed = successes == STOCK and conflicts == CUSTOMERS - STOCK and not failures \
        and stock == 0 and orders == STOCK and sold == STOCK
    if not passed:
        print("FAIL: stock race did not converge to the expected single-winner-per-unit result", file=sys.stderr)
        return 1
    print("PASS: exactly 10 of 50 concurrent checkouts won, stock is 0, and no write was lost")
    return 0


if __name__ == "__main__":
    sys.exit(main())
