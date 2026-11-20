# Testing

## Layers

| Layer | Command | What it covers |
| --- | --- | --- |
| Backend unit/integration | `cd backend && mvn test` | State machine, pricing, auth flow, payment signatures, order filters and tracking lookup |
| API smoke (live stack) | `infrastructure/scripts/smoke-test.sh` | 33 end-to-end checks against a running API |
| Stock race (live stack) | `infrastructure/scripts/stock-race-check.py` | 50 concurrent checkouts against stock 10 |
| Frontend unit | `cd frontend && npm test` | Formatting helpers and shared UI primitives |
| Browser + axe (built app) | `cd frontend && npm run test:e2e` | Public, customer, console and role journeys; WCAG A/AA in Chrome at 390 px |
| Frontend build | `cd frontend && npm run build` | TypeScript strict typecheck + production bundle |
| 250K search (live stack) | `python3 infrastructure/scripts/catalogue-load-check.py` | Real HTTP search p95 over a 250,000-row catalogue across one or more nodes |
| Sustained soak (live stack) | `python3 infrastructure/scripts/soak-check.py` | 60 seconds of concurrent search plus real COD orders across one or more nodes, with p95 gates |
| Lighthouse (built app) | CI job + `python3 infrastructure/scripts/lighthouse-check.py` | Five mobile runs of home and search; median scores, thresholds and Web Vitals |

The backend test profile uses in-memory H2 in PostgreSQL compatibility mode, so `mvn test` needs no
database. The JDBC URL declares `NON_KEYWORDS=VALUE` so Hibernate can create every table (the coupon
`value` column is otherwise reserved by H2). The suite currently runs **52 tests**. The smoke script
exercises the real PostgreSQL schema, Flyway V1–V7, HTTP security and the seeded order flow. CI runs the
backend and frontend gates, the Playwright/axe gate, a five-run Lighthouse job and the 60-second soak
gate against disposable PostgreSQL 16 services.

## Backend tests

- `OrderStateMachineTest` — legal and illegal transitions per role, cancellation windows.
- `PricingServiceTest` — subtotal, delivery fee thresholds, coupon caps, tax rounding.
- `AuthFlowTest` — registration, login, refresh rotation, OTP verification, RBAC rejection.
- `PaymentSignatureTest` — HMAC verification, tampered payload rejection, sandbox guard.
- `MedicineSearchTest` — unfiltered browse, term-less search, the PostgreSQL null-parameter regression and pharmacy-scoped stock filtering.
- `OrderQueryTest` — Active/Delivered/Cancelled filtering, status counts, pagination, keyed tracking lookup and public tracking privacy.
- `OrderReleaseTest` — cancellation and prescription rejection restore stock and coupon capacity exactly once, including the ledger entry and idempotency markers.
- `AdminQueryTest` — invalid admin approval filters return a client error instead of an unhandled 500.
- `MedicineSubstituteTest` — identical-composition filtering, pack/Rx/blocked/out-of-stock exclusion,
  savings and the paused-cheapest-pharmacy fallback.
- `OrderVersionTest` — a stale order write cannot overwrite a newer lifecycle transition.
- `DeliveryReassignTest` — a rejected rider is replaced without moving the order backwards.
- `SuspendedAuthorityTest` — suspended riders and pharmacies cannot advance existing work, review
  prescriptions, open the queue, read history/files or receive a new upload.
- `WebSocketAuthInterceptorTest` — suspended providers cannot subscribe to tracking/delivery
  channels, and clients cannot publish to the broker.
- `OrderSafetyTest` — tenant-scoped invoices, refund bypass refusal and two-channel verification.
- `AuthFlowTest` — includes locked-account OTP rejection and one-time code consumption.

Run a single class:

```bash
cd backend
mvn -Dtest=OrderStateMachineTest test
```

## Smoke test

```bash
# API must be running with seed data on :8080
infrastructure/scripts/smoke-test.sh
```

It checks catalogue browse and search (including the null-term path), pharmacy discovery, unauthenticated
401s, customer 403 on admin endpoints, COD order placement, pharmacy acceptance and preparation, delivery
auto-assignment, OTP handover, sandbox payment reconciliation and admin reporting. See the script header for
environment overrides.

## Stock race

The repeatable 50-way gate seeds 50 verified customers and carts directly in PostgreSQL, then fires
50 simultaneous COD checkouts against a real API and inventory row with stock 10:

```bash
python3 infrastructure/scripts/stock-race-check.py \
  --base-url http://127.0.0.1:8080 \
  --jwt-secret "$MEDIDROP_JWT_SECRET"
```

It requires PyJWT and `psql`, or `--psql-container` for a Docker-only host. A pass is exactly ten
`200` responses, forty `409` responses, ten orders, stock 0, ten sold ledger units and no other status.
The 0.1.7 run passed all of those conditions on fresh PostgreSQL 16.

## 250K catalogue search

The performance gate seeds **250,000** medicines and one inventory row each, then measures 100 real
HTTP searches distributed across one or more running APIs:

```bash
python3 infrastructure/scripts/catalogue-load-check.py \
  --base-url http://127.0.0.1:8080 \
  --base-url http://127.0.0.1:8081 \
  --rows 250000 --samples 100 --p95-ms 300 \
  --psql-container medidrop-gate
```

It requires `psql`, or `--psql-container` for a Docker-only host. A run fails on any HTTP error, any
empty result, an unused instance, or p95 at or above the target. The two-node run measured **p50 189.3
ms · p95 286.5 ms · p99 293.4 ms · 0 errors** after V7 added lowercased trigram expression indexes;
the slower node measured p95 **290.6 ms**. The separate soak gate below exercises sustained
concurrency. See `docs/PERF.md` for the environment and open targets.

## Sustained soak

The required CI smoke job starts two API instances against one PostgreSQL database, then runs a
60-second soak across both after the smoke suite:

```bash
python3 infrastructure/scripts/soak-check.py \
  --base-url http://127.0.0.1:8080 \
  --base-url http://127.0.0.1:8081 \
  --duration-seconds 60 --search-workers 3 --order-interval-seconds 2
```

It logs in as the documented demo customer, ensures an address, then distributes three search workers
across the nodes while placing real COD orders at two-second intervals. Each order is added on one
node and submitted on the next, so the run exercises shared cart and token state. It fails on any
transport/API error, empty search result, unused instance, order not reaching `PAYMENT_CONFIRMED`,
search p95 ≥ 300 ms or order p95 ≥ 800 ms. CI run `44780590024` completed **4,883 searches** at p95
**13.1 ms** and **31 orders** at p95 **61.8 ms**, zero errors. The local per-node run recorded 2,176 /
2,175 searches at p95 **16.2 ms** and orders at p95 **99.7 ms**. A single-instance run remains recorded
at **5,001 searches p95 7.9 ms** and **31 orders p95 28.7 ms**.

The search side uses the 18-row demo catalogue, not the 250K dataset; the 250K gate is the scale
test. The soak gate is about sustained concurrency and write-path stability.

## Frontend tests

```bash
cd frontend
npm test          # vitest run
npm run build     # tsc -b && vite build
npm run test:e2e  # Playwright + axe against the built preview
```

Vitest uses jsdom with `src/test/setup.ts`. Component tests avoid network calls; data fetching is tested
through the API smoke script instead of mocking every endpoint. The browser gate uses deterministic
API fixtures, so it does not require PostgreSQL. It checks four public pages and exercises search →
medicine detail, then seeds a customer, pharmacy, delivery or admin browser session and checks cart,
checkout, orders, order detail, prescriptions, profile, notifications, public tracking, all three
role dashboards and all nine console subpages. All twenty-five journeys run at 390 px in Chrome and
fail on WCAG A/AA axe violations. The gate is automated coverage, not a substitute for keyboard or
screen-reader audits.

Lighthouse runs in CI and as a local manual gate against the built preview with the API proxied:

```bash
# Terminal 1: API on :8080
cd frontend
VITE_PROXY_TARGET=http://127.0.0.1:8080 npm run preview -- --port 4174

# Terminal 2: five mobile runs per URL
python3 infrastructure/scripts/lighthouse-check.py --base-url http://127.0.0.1:4174 --runs 5
```

It writes `docs/assets/lighthouse-v018.json`. The local five-run median is **93** performance on home
and **92** on search, with accessibility **100** on both. The local runner enforces 90/100/100/90.
The CI job runs five samples and enforces a documented **75** performance floor because the hosted
runner measured **78 / 77**; accessibility and best-practices remain 100.

## Not covered automatically

- Real gateway flows (no live Razorpay credentials)
- Real email/SMS delivery
- Google Maps geocoding
- OCR extraction
- Cross-browser visual regression
- Production-network or 250K sustained mixed load, PostGIS/nearby-node queries and N+1 proof
- The 90 Lighthouse performance target on shared CI hardware (the CI floor is 75)
- Recorded keyboard and screen-reader passes
- Touch-target, Ops Night, i18n and 1440 px audits

These are listed in `docs/deployment.md` as go-live checks. Add contract tests against the provider sandbox
once credentials exist.
