#!/usr/bin/env python3
"""Runs the 250K catalogue search gate against a live MediDrop API.

This is an external black-box benchmark. It seeds a disposable PostgreSQL
database with a realistic synthetic catalogue and one inventory row per
medicine, then measures real HTTP search latency through the application.

The gate fails when the p95 is at or above the target, when any request errors,
or when any search returns no rows. The exact row count, query count, machine
and run date are printed with the result so the number is reproducible.

Requires: psql on PATH (or Docker with a psql container), Python 3.11+, and an
API already running against the same database.
"""

import argparse
import json
import os
import platform
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

MARKER = "load://catalogue"
TERMS = (
    "Paracetamol",
    "Amoxicillin",
    "Atorvastatin",
    "Metformin",
    "Omeprazole",
    "Cetirizine",
    "Azithromycin",
    "Losartan",
    "Salbutamol",
    "Pantoprazole",
)


def psql(args, sql, fetch=True):
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
    return result.stdout.strip() if fetch else None


def seed_catalogue(args):
    existing = int(psql(args, f"select count(*) from medicines where image_url = '{MARKER}';"))
    if existing < args.rows:
        print(f"Seeding {args.rows:,} catalogue rows (existing benchmark rows: {existing:,})...", flush=True)
        # Delete in dependency order so repeated runs stay idempotent. The marker is
        # reserved for this disposable benchmark and is never used by demo data.
        psql(args, f"""
            begin;
            delete from inventory_transactions where inventory_id in (
              select id from pharmacy_inventory where medicine_id in
              (select id from medicines where image_url = '{MARKER}'));
            delete from pharmacy_inventory where medicine_id in
              (select id from medicines where image_url = '{MARKER}');
            delete from medicines where image_url = '{MARKER}';

            insert into medicines
              (name, brand, generic_name, strength, dosage_form, pack_size,
               manufacturer_id, category_id, prescription_required, description,
               warnings, schedule_class, sales_blocked, image_url, active, is_demo,
               created_at, updated_at)
            select
              (array['Paracetamol','Amoxicillin','Atorvastatin','Metformin','Omeprazole',
                     'Cetirizine','Azithromycin','Losartan','Salbutamol','Pantoprazole'])[1 + (g % 10)]
                || ' ' || (array['5 mg','10 mg','20 mg','40 mg','250 mg','500 mg'])[1 + (g % 6)]
                || ' ' || (array['Tablet','Capsule','Syrup'])[1 + (g % 3)]
                || ' ' || lpad(g::text, 6, '0'),
              'MediDrop Load ' || (1 + (g % 40)),
              (array['Paracetamol','Amoxicillin','Atorvastatin','Metformin','Omeprazole',
                     'Cetirizine','Azithromycin','Losartan','Salbutamol','Pantoprazole'])[1 + (g % 10)],
              (array['5 mg','10 mg','20 mg','40 mg','250 mg','500 mg'])[1 + (g % 6)],
              (array['Tablet','Capsule','Syrup'])[1 + (g % 3)],
              (array['1 strip of 10','1 bottle of 100 ml','1 strip of 15'])[1 + (g % 3)],
              (select id from manufacturers order by id limit 1),
              (select id from medicine_categories order by id limit 1),
              (g % 10 = 0),
              'Synthetic catalogue row for the 250K load gate.',
              'Synthetic benchmark data; not for clinical use.',
              case when g % 10 = 0 then 'H' else 'OTC' end,
              false, '{MARKER}', true, false, now(), now()
            from generate_series(1, {args.rows}) g;

            insert into pharmacy_inventory
              (active, low_stock_threshold, mrp, price, reserved_quantity,
               stock_quantity, created_at, medicine_id, pharmacy_id, updated_at, version)
            select true, 10, round((30 + (m.id % 500) * 0.37)::numeric, 2),
                   round((20 + (m.id % 500) * 0.37)::numeric, 2),
                   0, 100, now(), m.id,
                   (select id from pharmacies order by id limit 1), now(), 0
            from medicines m where m.image_url = '{MARKER}';
            commit;
        """, fetch=False)
        psql(args, "analyze medicines; analyze pharmacy_inventory;", fetch=False)
    total = int(psql(args, f"select count(*) from medicines where image_url = '{MARKER}';"))
    if total < args.rows:
        raise RuntimeError(f"Expected at least {args.rows:,} benchmark rows, found {total:,}")
    return total


def search(base_url, term, timeout):
    query = urllib.parse.urlencode({
        "q": term,
        "lat": "22.5726",
        "lng": "88.3639",
        "size": "12",
    })
    request = urllib.request.Request(f"{base_url.rstrip('/')}/api/v1/medicines/search?{query}")
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode()
            elapsed = (time.perf_counter() - start) * 1000
            return response.status, elapsed, body
    except urllib.error.HTTPError as error:
        return error.code, (time.perf_counter() - start) * 1000, error.read().decode()
    except Exception as error:  # noqa: BLE001 - transport failure is a gate failure
        return 0, (time.perf_counter() - start) * 1000, repr(error)


def percentile(values, fraction):
    ordered = sorted(values)
    if not ordered:
        return 0.0
    index = min(len(ordered) - 1, max(0, int(round((len(ordered) - 1) * fraction))))
    return ordered[index]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--rows", type=int, default=250_000)
    parser.add_argument("--samples", type=int, default=100)
    parser.add_argument("--warmup", type=int, default=10)
    parser.add_argument("--p95-ms", type=float, default=300.0)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--pg-host", default="127.0.0.1")
    parser.add_argument("--pg-port", type=int, default=5432)
    parser.add_argument("--pg-user", default="medidrop")
    parser.add_argument("--pg-password", default="medidrop")
    parser.add_argument("--pg-db", default="medidrop")
    parser.add_argument("--psql-container", help="Run psql through this Docker container instead of the host")
    args = parser.parse_args()

    if args.rows < 1 or args.samples < 1:
        raise SystemExit("--rows and --samples must be positive")

    total = seed_catalogue(args)
    # Warm the JVM, connection pool, and PostgreSQL plan cache before sampling.
    for index in range(args.warmup):
        status, _, body = search(args.base_url, TERMS[index % len(TERMS)], args.timeout)
        if status != 200:
            raise RuntimeError(f"Warmup request failed with HTTP {status}: {body[:300]}")

    latencies = []
    errors = []
    empty = []
    for index in range(args.samples):
        term = TERMS[index % len(TERMS)]
        status, elapsed, body = search(args.base_url, term, args.timeout)
        latencies.append(elapsed)
        if status != 200:
            errors.append((status, body[:300]))
            continue
        try:
            payload = json.loads(body)
            items = payload.get("data", {}).get("items", [])
            total_elements = payload.get("data", {}).get("totalElements", 0)
            if not items or total_elements < 1:
                empty.append(term)
        except json.JSONDecodeError:
            errors.append((status, "non-JSON response"))

    p50 = percentile(latencies, 0.50)
    p95 = percentile(latencies, 0.95)
    p99 = percentile(latencies, 0.99)
    report = {
        "dataset_rows": total,
        "samples": args.samples,
        "warmup": args.warmup,
        "p50_ms": round(p50, 1),
        "p95_ms": round(p95, 1),
        "p99_ms": round(p99, 1),
        "max_ms": round(max(latencies), 1),
        "target_p95_ms": args.p95_ms,
        "errors": len(errors),
        "empty_results": len(empty),
        "machine": platform.platform(),
        "python": platform.python_version(),
        "run_at": datetime.now(timezone.utc).isoformat(),
    }
    print(json.dumps(report, indent=2))

    if errors:
        print(f"Gate failed: {len(errors)} request errors, first={errors[0]}", file=sys.stderr)
        return 1
    if empty:
        print(f"Gate failed: {len(empty)} searches returned no rows, first={empty[0]}", file=sys.stderr)
        return 1
    if p95 >= args.p95_ms:
        print(f"Gate failed: p95 {p95:.1f} ms is not below {args.p95_ms:.1f} ms", file=sys.stderr)
        return 1
    print(f"PASS: p95 {p95:.1f} ms < {args.p95_ms:.1f} ms across {args.samples} requests")
    return 0


if __name__ == "__main__":
    sys.exit(main())
