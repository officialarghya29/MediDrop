# Testing

## Layers

| Layer | Command | What it covers |
| --- | --- | --- |
| Backend unit/integration | `cd backend && mvn test` | State machine, pricing, auth flow, payment signatures |
| API smoke (live stack) | `infrastructure/scripts/smoke-test.sh` | 31 end-to-end checks against a running API |
| Frontend unit | `cd frontend && npm test` | Formatting helpers and shared UI primitives |
| Frontend build | `cd frontend && npm run build` | TypeScript strict typecheck + production bundle |

The backend test profile uses in-memory H2 in PostgreSQL compatibility mode, so `mvn test` needs no
database. The smoke script exercises the real PostgreSQL schema, Flyway migrations and HTTP security.

## Backend tests

- `OrderStateMachineTest` — legal and illegal transitions per role, cancellation windows.
- `PricingServiceTest` — subtotal, delivery fee thresholds, coupon caps, tax rounding.
- `AuthFlowTest` — registration, login, refresh rotation, OTP verification, RBAC rejection.
- `PaymentSignatureTest` — HMAC verification, tampered payload rejection, sandbox guard.

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

It checks catalogue and pharmacy discovery, unauthenticated 401s, customer 403 on pharmacy endpoints, COD
order placement, pharmacy acceptance and preparation, delivery auto-assignment, OTP handover, sandbox
payment reconciliation and admin reporting. See the script header for environment overrides.

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
- Load and soak testing

These are listed in `docs/deployment.md` as go-live checks. Add contract tests against the provider sandbox
once credentials exist.
