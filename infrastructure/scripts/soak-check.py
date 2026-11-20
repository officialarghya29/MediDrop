#!/usr/bin/env python3
"""Runs a sustained catalogue-search and order-placement soak gate.

The gate drives a real API with the demo seed: several workers search the
catalogue continuously while a separate worker places real cash-on-delivery
orders through the customer HTTP flow. Search and order latency are reported
together with the dataset, duration and machine so the result is reproducible.

The check fails on any transport/API error, any empty search result, or when
either p95 is at or above its target. It is intentionally external to the
application and uses only the public HTTP API plus the documented demo account.

Requires: Python 3.11+ and an API started with the demo seed.
"""

import argparse
import json
import platform
import statistics
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone

SEARCH_TERMS = (
    "Paracetamol",
    "Amoxicillin",
    "Cetirizine",
    "Azithromycin",
    "Metformin",
    "Pantoprazole",
    "Ibuprofen",
    "ORS",
    "Vitamin D3",
    "Dolo",
)

# OTC demo products. The seeder gives each listing 40-85 units, so a 60-second
# run at the default interval has enough stock without needing a reset.
ORDER_TERMS = (
    "Paracetamol",
    "Dolo",
    "Cetirizine",
    "Ibuprofen",
    "ORS",
    "Vitamin D3",
    "Digital Thermometer",
    "Blood Pressure Monitor",
    "Antiseptic Liquid",
    "Sterile Gauze",
    "Hand Sanitiser",
)

AUTH_FAILED = "authentication failed"


def request(base, path, method="GET", body=None, token=None, timeout=30.0, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    request_headers = {"Accept": "application/json"}
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        request_headers["Content-Type"] = "application/json"
    if headers:
        request_headers.update(headers)
    req = urllib.request.Request(f"{base}{path}", data=data, headers=request_headers, method=method)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode()
            elapsed = (time.perf_counter() - start) * 1000
            payload = json.loads(raw) if raw else None
            return response.status, elapsed, payload, None
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        return error.code, (time.perf_counter() - start) * 1000, None, raw[:500]
    except Exception as error:  # noqa: BLE001 - any transport failure is a gate failure
        return 0, (time.perf_counter() - start) * 1000, None, repr(error)


def percentile(values, fraction):
    ordered = sorted(values)
    if not ordered:
        return 0.0
    index = min(len(ordered) - 1, max(0, int(round((len(ordered) - 1) * fraction))))
    return ordered[index]


def login(base, identifier, password, timeout):
    status, _, payload, error = request(
        base, "/auth/login", "POST", {"identifier": identifier, "password": password}, timeout=timeout
    )
    if status != 200 or not payload:
        raise RuntimeError(f"login failed with HTTP {status}: {error or payload}")
    return payload["data"]["accessToken"]


def ensure_address(base, token, timeout):
    status, _, payload, error = request(base, "/customer/addresses", token=token, timeout=timeout)
    if status != 200 or payload is None:
        raise RuntimeError(f"address lookup failed with HTTP {status}: {error or payload}")
    addresses = payload.get("data") or []
    if addresses:
        return addresses[0]["id"]

    body = {
        "label": "Home",
        "recipientName": "Soak Gate Customer",
        "phone": "9800000002",
        "line1": "DN 51, Sector V",
        "line2": "Near City Centre",
        "city": "Kolkata",
        "state": "West Bengal",
        "pincode": "700091",
        "latitude": 22.5726,
        "longitude": 88.3639,
        "defaultAddress": True,
    }
    status, _, payload, error = request(
        base, "/customer/addresses", "POST", body, token=token, timeout=timeout
    )
    if status != 200 or not payload:
        raise RuntimeError(f"address creation failed with HTTP {status}: {error or payload}")
    return payload["data"]["id"]


def clear_cart(base, token, timeout):
    status, _, _, error = request(base, "/cart", "DELETE", token=token, timeout=timeout)
    if status != 200:
        raise RuntimeError(f"cart clear failed with HTTP {status}: {error}")


def find_inventory(base, term, timeout):
    query = urllib.parse.urlencode(
        {"q": term, "lat": "22.5726", "lng": "88.3639", "size": "12"}
    )
    status, _, payload, error = request(base, f"/medicines/search?{query}", timeout=timeout)
    if status != 200 or not payload:
        return None, f"search for {term} failed with HTTP {status}: {error or payload}"

    for item in payload.get("data", {}).get("items", []):
        if item.get("prescriptionRequired") or item.get("totalStock", 0) < 1:
            continue
        status, _, detail, error = request(
            base,
            f"/medicines/{item['id']}?lat=22.5726&lng=88.3639",
            timeout=timeout,
        )
        if status != 200 or not detail:
            continue
        for option in detail.get("data", {}).get("availability", []):
            if option.get("acceptingOrders") and option.get("availableQuantity", 0) >= 1:
                return option["inventoryId"], None
    return None, f"no purchasable OTC inventory found for {term}"


def search_once(base, term, timeout):
    query = urllib.parse.urlencode(
        {"q": term, "lat": "22.5726", "lng": "88.3639", "size": "12"}
    )
    status, elapsed, payload, error = request(base, f"/medicines/search?{query}", timeout=timeout)
    if status != 200 or not payload:
        return elapsed, f"HTTP {status}: {error or payload}"
    data = payload.get("data") or {}
    if not data.get("items") or data.get("totalElements", 0) < 1:
        return elapsed, f"empty result for {term}"
    return elapsed, None


def place_order(base, token, address_id, inventory_id, timeout):
    clear_cart(base, token, timeout)
    status, _, _, error = request(
        base, "/cart/items", "POST", {"inventoryId": inventory_id, "quantity": 1},
        token=token, timeout=timeout,
    )
    if status != 200:
        return None, f"add-to-cart failed with HTTP {status}: {error}"

    body = {
        "addressId": address_id,
        "paymentMethod": "COD",
        "note": "Sustained load soak gate",
    }
    status, elapsed, payload, error = request(
        base,
        "/orders",
        "POST",
        body,
        token=token,
        timeout=timeout,
        headers={"X-Idempotency-Key": f"soak-{uuid.uuid4()}"},
    )
    if status != 200 or not payload:
        return elapsed, f"order failed with HTTP {status}: {error or payload}"
    order = payload.get("data") or {}
    if order.get("status") != "PAYMENT_CONFIRMED":
        return elapsed, f"order {order.get('orderNumber')} has status {order.get('status')}"
    return elapsed, None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--duration-seconds", type=float, default=60.0)
    parser.add_argument("--search-workers", type=int, default=3)
    parser.add_argument("--order-interval-seconds", type=float, default=2.0)
    parser.add_argument("--search-p95-ms", type=float, default=300.0)
    parser.add_argument("--order-p95-ms", type=float, default=800.0)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--customer", default="customer@medidrop.local")
    parser.add_argument("--password", default="Demo@12345")
    args = parser.parse_args()

    if args.duration_seconds <= 0 or args.search_workers < 1:
        raise SystemExit("--duration-seconds and --search-workers must be positive")
    if args.order_interval_seconds <= 0:
        raise SystemExit("--order-interval-seconds must be positive")

    base = args.base_url.rstrip("/") + "/api/v1"
    try:
        token = login(base, args.customer, args.password, args.timeout)
        address_id = ensure_address(base, token, args.timeout)
        clear_cart(base, token, args.timeout)
    except RuntimeError as error:
        print(f"Setup failed: {error}", file=sys.stderr)
        return 1

    stop = threading.Event()
    search_latencies = []
    search_errors = []
    order_latencies = []
    order_errors = []
    fatal_errors = []
    lock = threading.Lock()

    def search_worker(worker_index):
        counter = worker_index
        while not stop.is_set():
            term = SEARCH_TERMS[counter % len(SEARCH_TERMS)]
            elapsed, error = search_once(base, term, args.timeout)
            with lock:
                search_latencies.append(elapsed)
                if error:
                    search_errors.append(error)
            counter += 1
            time.sleep(0.03)

    def order_worker():
        counter = 0
        while not stop.is_set():
            started = time.monotonic()
            term = ORDER_TERMS[counter % len(ORDER_TERMS)]
            inventory_id, error = find_inventory(base, term, args.timeout)
            if error:
                with lock:
                    order_errors.append(error)
            else:
                elapsed, error = place_order(
                    base, token, address_id, inventory_id, args.timeout
                )
                with lock:
                    if error:
                        order_errors.append(error)
                    elif elapsed is not None:
                        order_latencies.append(elapsed)
            counter += 1
            remaining = args.order_interval_seconds - (time.monotonic() - started)
            if remaining > 0:
                stop.wait(remaining)

    def run_thread(name, target, *args_):
        try:
            target(*args_)
        except Exception as error:  # noqa: BLE001 - thread failure must fail the gate
            with lock:
                fatal_errors.append(f"{name}: {error!r}")

    started_at = time.monotonic()
    threads = [
        threading.Thread(target=run_thread, args=(f"search-{index}", search_worker, index), daemon=True)
        for index in range(args.search_workers)
    ]
    threads.append(threading.Thread(target=run_thread, args=("orders", order_worker), daemon=True))
    for thread in threads:
        thread.start()

    while time.monotonic() - started_at < args.duration_seconds:
        time.sleep(0.25)
    stop.set()
    for thread in threads:
        thread.join(timeout=args.timeout)
    elapsed_seconds = time.monotonic() - started_at

    sp50 = percentile(search_latencies, 0.50)
    sp95 = percentile(search_latencies, 0.95)
    sp99 = percentile(search_latencies, 0.99)
    op50 = percentile(order_latencies, 0.50)
    op95 = percentile(order_latencies, 0.95)
    op99 = percentile(order_latencies, 0.99)
    report = {
        "gate": "sustained-search-and-order",
        "duration_seconds": round(elapsed_seconds, 1),
        "search_workers": args.search_workers,
        "order_interval_seconds": args.order_interval_seconds,
        "search": {
            "samples": len(search_latencies),
            "p50_ms": round(sp50, 1),
            "p95_ms": round(sp95, 1),
            "p99_ms": round(sp99, 1),
            "max_ms": round(max(search_latencies), 1) if search_latencies else 0.0,
            "target_p95_ms": args.search_p95_ms,
            "errors": len(search_errors),
        },
        "orders": {
            "samples": len(order_latencies),
            "p50_ms": round(op50, 1),
            "p95_ms": round(op95, 1),
            "p99_ms": round(op99, 1),
            "max_ms": round(max(order_latencies), 1) if order_latencies else 0.0,
            "target_p95_ms": args.order_p95_ms,
            "errors": len(order_errors),
        },
        "machine": platform.platform(),
        "python": platform.python_version(),
        "run_at": datetime.now(timezone.utc).isoformat(),
    }
    print(json.dumps(report, indent=2))

    failures = []
    if fatal_errors:
        failures.append(f"{len(fatal_errors)} worker failures, first={fatal_errors[0]}")
    if not search_latencies:
        failures.append("no completed search requests")
    if search_errors:
        failures.append(f"{len(search_errors)} search errors, first={search_errors[0]}")
    if not order_latencies:
        failures.append("no completed orders")
    if order_errors:
        failures.append(f"{len(order_errors)} order errors, first={order_errors[0]}")
    if sp95 >= args.search_p95_ms:
        failures.append(f"search p95 {sp95:.1f} ms is not below {args.search_p95_ms:.1f} ms")
    if op95 >= args.order_p95_ms:
        failures.append(f"order p95 {op95:.1f} ms is not below {args.order_p95_ms:.1f} ms")

    if failures:
        for failure in failures:
            print(f"Gate failed: {failure}", file=sys.stderr)
        return 1

    print(
        f"PASS: {len(search_latencies)} searches at p95 {sp95:.1f} ms and "
        f"{len(order_latencies)} orders at p95 {op95:.1f} ms over {elapsed_seconds:.0f}s"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
