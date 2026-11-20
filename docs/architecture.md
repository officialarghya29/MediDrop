# MediDrop architecture

MediDrop is a **modular monolith** with a React single-page application. It is deliberately one
deployable API: order placement touches inventory, pricing, prescriptions and payments in a single
database transaction, and splitting those today would add distributed-transaction complexity without
a scaling need.

```text
Browser (React 18 + Vite)
   │  HTTPS  /api/v1            WSS /ws (STOMP)
   ▼
nginx  ── static SPA, reverse proxy, TLS termination
   │
   ▼
Spring Boot 3.5 / Java 21
   ├── security      JWT, Argon2id, RBAC, rate limiter
   ├── catalogue     medicines, categories, search
   ├── pharmacy      onboarding, discovery, hours, documents
   ├── inventory     listings, stock ledger, batches, expiry
   ├── cart          single-pharmacy cart, live revalidation
   ├── order         state machine, pricing, invoices, reviews
   ├── prescription  private uploads, pharmacist review
   ├── payment       Razorpay + sandbox, webhooks, refunds
   ├── delivery      assignment, OTP handover, live location
   ├── admin         approvals, users, settings, coupons, reports
   └── audit         append-only audit trail + notifications
   │
   ▼
PostgreSQL 16 (Flyway migrations, pg_trgm search index)
Local disk volume for prescription files (FileStorage abstraction)
```

## Modules and boundaries

Each package under `com.medidrop` owns its entities, repositories, services and controllers. Cross-module
references are stored as scalar IDs (`pharmacyId`, `customerId`, `orderId`) rather than JPA associations, so
query paths stay explicit and N+1 surprises are avoided. Services expose intent-level methods
(`OrderService.place`, `DeliveryService.autoAssign`, `PrescriptionService.review`) and controllers never
call repositories from another module.

| Module | Owns | Notes |
| --- | --- | --- |
| `auth` / `security` | users, refresh tokens, OTPs | Argon2id hashes, hashed refresh tokens, rotating sessions |
| `medicine` | medicines, categories, manufacturers | pg_trgm-backed fuzzy search with suggestions |
| `pharmacy` | pharmacies, documents, opening hours | approval workflow and delivery radius |
| `inventory` | listings, batches, stock ledger | every stock change is an immutable ledger row |
| `cart` | carts, cart items | one pharmacy per cart, re-priced on read |
| `order` | orders, items, history, reviews | single state machine as source of truth |
| `prescription` | prescription files, reviews | access-controlled streaming endpoints |
| `payment` | payments, refunds | provider abstraction with HMAC verification |
| `delivery` | partners, assignments, pings | WebSocket tracking keyed by unguessable token |
| `admin` | governance endpoints, reporting | approvals, suspensions, platform metrics |
| `notification`, `support`, `audit` | alerts, tickets, audit rows | audit is append-only |

## Order request flow

1. **Cart validation** — cart lines are re-priced from current inventory; stock, pharmacy status,
   minimum order value and delivery radius are checked server-side.
2. **Placement** — `X-Idempotency-Key` deduplicates retries. The order row, items, initial history entry
   and prescription link are written in one transaction. Stock is *reserved* through the ledger.
3. **Payment** — COD confirms immediately; online methods create a provider order and return a checkout
   session. Settlement is accepted only after signature verification (or the sandbox endpoint in dev).
4. **Prescription gate** — orders containing Schedule H items cannot leave
   `PRESCRIPTION_REVIEW_PENDING` until a pharmacist approves the attached prescription.
5. **Fulfilment** — pharmacy accepts, prepares, marks ready; the platform auto-assigns an online partner;
   the partner progresses pickup → out for delivery; handover requires the customer's OTP.
6. **Aftercare** — invoices are generated from stored line items; cancellation and refund follow the
   state machine; reviews are allowed once delivered.

Every transition is validated by `OrderStateMachine` **and** by the endpoint's role check. Server-driven
steps (payment settlement, auto-assignment, pharmacist decisions) use `assertSystemAllowed`, which still
rejects out-of-order edges.

## Data and consistency

- **Transactions** wrap order placement, stock changes, payment settlement and refunds.
- **Optimistic locking** protects pharmacy inventory rows (`@Version`).
- **Append-only ledger** records every stock movement with actor, reason and balance.
- **Flyway** owns schema changes; Hibernate runs with `ddl-auto: validate`, so drift fails fast at boot.
- **Time** is stored in UTC and rendered in `Asia/Kolkata` for users.

## Security model

| Area | Control |
| --- | --- |
| Passwords | Argon2id with per-user salt |
| Access tokens | HMAC-signed JWT, 15 minutes, role claim, issuer check |
| Refresh tokens | Opaque 256-bit values, only SHA-256 hashes stored, rotated on every use |
| OTP | Hashed, attempt-limited, expiry-bound, never returned outside dev |
| Authorisation | Method-level `@PreAuthorize` plus resource-ownership checks in services |
| Prescriptions | Private storage; download requires an authenticated owner, linked pharmacy or admin |
| Payments | HMAC-SHA256 signature verification, webhook replay-safe state transitions |
| Rate limiting | In-memory sliding window on login, OTP and password reset endpoints |
| Auditing | Append-only audit rows for approvals, suspensions, refunds and status overrides |
| Headers | nginx adds nosniff, frame, referrer and permissions policies |

The SPA stores JWTs in `localStorage`. This is a documented trade-off that keeps the API stateless and
works without same-site cookies across domains; the mitigation is short access-token lifetime plus refresh
rotation. Deployments that require cookie-based sessions should move tokens to `HttpOnly` cookies and add
CSRF protection.

## Integration seams

External services are behind interfaces so the platform runs without credentials:

- `PaymentGateway` — Razorpay implementation and a sandbox implementation. The sandbox refuses to load
  under the `prod` profile and marks every payment `is_test=true`.
- `MapsProvider` — Google Geocoding implementation plus Haversine distance fallback. The fallback refuses
  geocoding requests instead of inventing coordinates.
- `FileStorage` — local disk today; S3 can be added without touching callers.
- `OcrService` — disabled by default; pharmacist review never depends on OCR.
- Mail/SMS — console providers in development, SMTP/console in production until credentials are supplied.

## Deliberate limits

- Single-region deployment; no read replicas.
- Rate limiting is per instance, not distributed.
- Forecasts are a simple 30-day weighted average, not a trained model.
- No microservice split, message broker or event sourcing.

These are documented so operators know what must change before scaling beyond a single city cluster.
