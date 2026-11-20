# Testing

## Layers

| Layer | Command | What it covers |
| --- | --- | --- |
| Backend unit/integration | `cd backend && mvn test` | State machine, pricing, auth flow, payment signatures, order filters and tracking lookup |
| API smoke (live stack) | `infrastructure/scripts/smoke-test.sh` | 33 end-to-end checks against a running API |
| Stock race (live stack) | `infrastructure/scripts/stock-race-check.py` | 50 concurrent checkouts against stock 10 |
| Frontend unit | `cd frontend && npm test` | Formatting helpers and shared UI primitives |
| Frontend build | `cd frontend && npm run build` | TypeScript strict typecheck + production bundle |

The backend test profile uses in-memory H2 in PostgreSQL compatibility mode, so `mvn test` needs no
database. The JDBC URL declares `NON_KEYWORDS=VALUE` so Hibernate can create every table (the coupon
`value` column is otherwise reserved by H2). The suite currently runs **52 tests**. The smoke script
exercises the real PostgreSQL schema, Flyway V1–V6, HTTP security and the seeded order flow. CI runs the
backend and frontend gates plus the same smoke script against a disposable PostgreSQL 16 service.

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
The 0.1.6 run passed all of those conditions on fresh PostgreSQL 16.

## Frontend tests

```bash
cd frontend
npm test          # vitest run
npm run build     # tsc -b && vite build
```

Vitest uses jsdom with `src/test/setup.ts`. Component tests avoid network calls; data fetching is tested
through the API smoke script instead of mocking every endpoint.

## Not covered automatically

- Real gateway flows (no live Razorpay credentials)
- Real email/SMS delivery
- Google Maps geocoding
- OCR extraction
- Cross-browser visual regression
- Sustained load and soak testing (the 50-way burst is now covered; the 250K/long-run gate is not)

These are listed in `docs/deployment.md` as go-live checks. Add contract tests against the provider sandbox
once credentials exist.
