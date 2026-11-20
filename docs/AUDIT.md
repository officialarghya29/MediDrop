# M0 repository audit

- Audit date: 2026-11-20
- Repository: `officialarghya29/MediDrop` (private)
- Branch: `main`
- Audited commit: `0e9b76c79b39b1139f57681500448dbb55d4b9f8`
- Audit process: clean API process on `:8081`, PostgreSQL 16 on `:5433`, seeded demo database
- Application code changed in M0: **none**. This file and the other `docs/` memory files are the only M0 changes.

## 1. Scope and checklist basis

The supplied Addendum v3 includes Part 2 and repeatedly references the **36 checkboxes in original Section 24**, but the 36 lines themselves were not included in the addendum or found in the repository or its history.

The matrix below is therefore a **36-item reconstruction** from the supplied addendum, the existing product surface, `docs/order-lifecycle.md`, and the current API. It is not claimed to be a verbatim copy of the unavailable original Section 24. Exact acceptance sign-off is **BLOCKED** until the original 36 lines are reconciled against this matrix.

The audit still applies the addendum's evidence rule: no item below receives a status without a file path, command result, test result or live HTTP result.

## 2. Verdict

**Not launch-ready.** The codebase is materially stronger than a demo: PostgreSQL migrations, server-side pricing, JWT rotation, private prescription files, a single order state machine, real database-backed dashboards, a working 33-check smoke suite, and CI all function.

M0 nevertheless found **five P0 issues** that block real healthcare use:

1. A pharmacy can read another pharmacy's order invoice.
2. An admin can mark a delivered order `REFUNDED` without creating a refund or calling the gateway.
3. There is no separate, verified pharmacist role; the pharmacy owner account approves prescriptions.
4. Controlled/restricted medicine handling and CDSCO schedule classification are absent.
5. The customer-facing handover OTP is not returned on the order page (only buried in a notification), so the visible UI cannot complete delivery without leaving the order screen.

The repository must not be described as launch-ready, legally compliant, or production-certified until the P0 items, external provider certification and legal review are closed.

## 3. Environment, build and dependency evidence

| Check | Command | Result |
| --- | --- | --- |
| Backend tests | `cd backend && mvn -B -ntp test` | **28 passed, 0 failed** |
| Frontend tests | `cd frontend && npm test` | **13 passed, 0 failed** |
| Frontend production build | `cd frontend && npm run build` | **passed** (1819 modules, Vite 8.3.8) |
| npm audit | `npm audit --json` | **0 vulnerabilities** at every severity |
| Maven OSV batch | 101 runtime packages queried against `api.osv.dev` | **2 advisories**, both `spring-webmvc 6.2.19` |
| Secret history | Gitleaks Docker, 11 commits | **no leaks found** |
| Compose validation | `docker compose config` with test secrets | **exit 0** |
| Compose from example | `docker compose --env-file .env.example config` with required secrets added | **exit 0** |
| nginx syntax | previous M0 infrastructure run (`nginx -t` in Docker) | **passed** |
| OpenAPI after clean boot | `GET /v3/api-docs` | **200**, 96,425 bytes, ~1.5 s |
| CI at audited commit | GitHub Actions run `44698246524` | **3/3 jobs successful** |

Residual Maven advisories:

- `GHSA-j9f9-w8pj-32f8` — Spring WebMVC SSE fragment handling.
- `GHSA-pc63-qcmh-9cmg` — Spring WebMVC XSLT view handling.

No Spring 6.2.x backport exists at audit time. The API does not use SSE fragments or XSLT views. This is a documented residual risk, not a clean audit. Moving to Spring Framework 7 is required to close it.

### Stack

| Layer | Version |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| React | 18.3.1 |
| Vite | 8.3.8 |
| Node requirement | >= 22.12.0 |
| PostgreSQL | 16 |
| Flyway | V1–V4 |
| Auth | JWT HS256, Argon2id, opaque rotating refresh tokens |
| Realtime | STOMP over native WebSocket |

### How to start

Docker, from the repository root:

```bash
cp .env.example .env
# Fill MEDIDROP_JWT_SECRET and POSTGRES_PASSWORD.
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8080/actuator/health
```

Manual development:

```bash
createdb medidrop
cd backend
MEDIDROP_JWT_SECRET=dev-only-medidrop-secret-change-before-deploy-9f3c1a \
MEDIDROP_SEED_ENABLED=true \
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

Demo accounts are in `README.md`; all are dev/demo only. Production must use `SPRING_PROFILES_ACTIVE=prod` with seeding disabled.

> Operational trap found during M0: rebuilding `target/medidrop-api-1.0.0.jar` while a JVM is running from that jar corrupts lazy class loading (`NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy`) and creates misleading timeouts. Stop the JVM before rebuilding, then start a clean process. The audit used a fresh `:8081` process for this reason.

## 4. The 36-item acceptance matrix

Status meanings: **VERIFIED** = works and has direct evidence; **PARTIAL** = exists but has a material gap; **STUB** = interface/demo only; **MISSING** = not implemented; **BROKEN** = implemented but fails under the stated test.

| # | Acceptance item | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Customer registration, login and session | **VERIFIED** | New customer B registered, verified email and logged in (`/auth/register`, `/auth/otp/verify`, `/auth/login` all `200`); six seeded role accounts logged in. |
| 2 | Email and phone verification before ordering | **PARTIAL** | `OrderService.place` permits ordering when **either** email or phone is verified. Customer B never verified phone but could add an address and reach checkout. Code: `OrderService.java`. |
| 3 | JWT access token and refresh rotation | **VERIFIED** | Refresh returned `200` with a new token; replaying the old refresh token returned `401`. Code: `AuthService.refresh`, `JwtService`. |
| 4 | Four-role RBAC | **VERIFIED** | `SecurityConfig`; unauthenticated `/orders` → `401`; customer → `/admin/dashboard` → `403`; pharmacy 2 → pharmacy 1's order detail → `404`. |
| 5 | Separate verified pharmacist identity | **MISSING** | `Role.java` has only four roles. `PrescriptionService.review` resolves the actor by `pharmacyRepository.findByOwnerUserId`, with no registration number, state-council verification or separate pharmacist account. |
| 6 | Secure admin bootstrap | **PARTIAL** | Dev seed creates `admin@medidrop.local` with a documented default password. `MEDIDROP_ADMIN_PASSWORD` is optional and there is no forced rotation or one-time bootstrap token. Production seeding is disabled, but no secure production bootstrap exists. |
| 7 | Login, OTP and reset abuse controls | **PARTIAL** | `RateLimitFilter` enforces 20 requests/minute per IP + path; 21 reset requests produced one `429`. Five failed logins followed by a correct password still returned `200`; no per-account/device lockout or distributed limiter. |
| 8 | OTP delivery provider | **PARTIAL** | `SmsSender` has console and SMTP-adjacent seams; no real SMS gateway. OTPs are hashed and attempt-limited, but email/SMS delivery is console-only in this environment. |
| 9 | Partner onboarding, licence and document verification | **PARTIAL** | Pharmacy and delivery registration plus document endpoints exist. Admin approval changes status without requiring or independently validating uploaded documents; no registry/expiry check. |
| 10 | Admin approvals and suspensions | **PARTIAL** | Admin dashboard, approvals and suspension endpoints exist. Approval is a status change, not a document/content review; audit viewer is broken (see #32). |
| 11 | Catalogue, categories and manufacturers | **PARTIAL** | 15 demo medicines, 7 categories and 3 manufacturers; category CRUD and search work. No admin manufacturer CRUD and no 250k/500-product catalogue. |
| 12 | Typo-tolerant search with quality gate | **PARTIAL** | `paracetmol` returned `200` with **0 items** and suggestions (`Pantoprazole`, `Paracetamol`). Search exists, but there is no automatic typo correction gate and no 300-query/top-3 ≥95% evaluation. |
| 13 | Dataset pipeline and provenance | **MISSING** | No downloader, staging, normalization, dedupe, quarantine, import report or `data_quality_issues` table. `infrastructure/scripts` contains only smoke/report scripts. No dataset was downloaded or used. |
| 14 | Rx classification, schedules and restricted products | **MISSING** | Boolean `prescription_required` and loose `schedule_class` only. No `UNKNOWN → REQUIRED` rule, CDSCO H/H1/X model, restricted/blocked product enforcement or legal upload gate. |
| 15 | Inventory listing and immutable stock ledger | **VERIFIED** | `pharmacy_inventory` + `inventory_transactions`; stock set to 1 and restored to 34 through the pharmacy API; ledger rows are written for restock/adjustment/sale/return. |
| 16 | Batch and expiry management | **PARTIAL** | Batch rows, expiry-date checks, expiring/low-stock endpoints exist. Available stock ignores batch expiry, sale does not allocate a batch, and stock can diverge from batch quantities. |
| 17 | Price never above MRP | **PARTIAL** | Live request with `price=999, mrp=10` returned `400 Selling price cannot exceed MRP`. `V1__initial_schema.sql` has **no** `CHECK (price <= mrp)`, so direct DB writes can violate the rule. |
| 18 | No overselling under concurrency | **PARTIAL** | Two customers raced for one unit. Exactly one order was created (`200`); the other failed with **`500`**, not `409`. Final stock was 0. Root stack: `ObjectOptimisticLockingFailureException` for `PharmacyInventory#2` at `OrderService.place` line 153. |
| 19 | Single-pharmacy cart with live revalidation | **VERIFIED** | Cart is single-pharmacy; `CartService.checkoutSnapshot` re-reads inventory and price; `OrderService.place` revalidates stock under lock. |
| 20 | Backend-calculated totals, tax, delivery and coupons | **VERIFIED** | `PricingServiceTest` passes; order total is computed server-side and returned on placement. Coupon rows are locked for update before redemption. |
| 21 | Idempotent order placement | **PARTIAL** | Sequential replay with the same `X-Idempotency-Key` returned the same order ID (`200` both times). Concurrent same-key behavior is untested; a unique constraint can still surface as a conflict rather than an idempotent response. |
| 22 | Order state machine and illegal transitions | **BROKEN** | Happy path works. Admin `PATCH` moved a **delivered** order directly to `REFUNDED` with `200`; refund ledger `0 → 0`. `OrderStateMachine` explicitly allows `DELIVERED → REFUNDED`, `CANCELLED → REFUNDED` and `FAILED → REFUNDED`. |
| 23 | Cash on delivery | **VERIFIED** | COD order moved through pharmacy, assignment, rider and OTP delivery. |
| 24 | Online payments, webhooks and refunds | **PARTIAL** | UPI sandbox checkout → completion returned `CAPTURED`; order became `PAYMENT_CONFIRMED`. Invalid webhook signature returned `401`; duplicate-capture code exists. Live Razorpay, provider replay and gateway refunds were not certified, and direct admin status changes bypass refunds. |
| 25 | Cancellation, returns and disputes | **PARTIAL** | Customer cancellation and stock restoration work; refunds are admin-only. Direct admin status changes bypass payment refunds. Returns, damaged/near-expiry disputes and customer-initiated refund rules are absent. |
| 26 | Prescription upload, validation and private storage | **VERIFIED** | PNG upload returned `200`; text file declared as `image/png` returned `400` (magic-byte check); download used `no-store`; storage is a local private volume. |
| 27 | Prescription access isolation | **PARTIAL** | Customer B and pharmacy 2 both received `403` on the file endpoint. However, pharmacy 2 received `200` and one review row from pharmacy 1's review history (`GET /pharmacy/prescriptions/{id}/reviews`). |
| 28 | Pharmacist review gate and OCR assist | **PARTIAL** | The approval gate blocks pharmacy acceptance until approved; approval transitions the order. The approver is the pharmacy owner, not a separately verified pharmacist. OCR is disabled (`DisabledOcrService`) and correctly not required for approval. |
| 29 | Pharmacy fulfilment queue | **VERIFIED** | Pharmacy accept, prepare and ready-for-pickup transitions all returned `200`; queue is database-backed. |
| 30 | Delivery, tracking, WebSocket security and public privacy | **VERIFIED** | Admin assignment; rider accept → at pharmacy → picked up → out for delivery all returned `200`. Four WebSocket tests pass. Public `/tracking/{key}` returned `200` with no address line, no recipient phone and only `Delivery area, Kolkata`. |
| 31 | OTP proof of delivery and customer-visible handover | **PARTIAL** | Wrong OTP returned `400`; correct 4-digit OTP returned `200` and `DELIVERED`. The customer order response returned `deliveryOtp: null`; the only usable OTP was in a notification. The UI expects `deliveryOtp` and the rider UI says “6-digit” while the backend generates four. |
| 32 | Admin control tower, reports, settings and audit viewer | **PARTIAL** | Dashboard and platform report returned `200` with database-backed figures. Unfiltered `/admin/audit-logs` returned **`500`**; `?action=LOGIN_SUCCESS` returned `200`. No live heatmap or command palette. Settings updates are not audited. |
| 33 | GST invoice and downloadable invoice | **PARTIAL** | Invoice includes order, seller, GSTIN, drug licence, buyer, line items, tax and payment method. No HSN line data, server-side invoice sequence or server-generated PDF; “download” is a browser print window. |
| 34 | Notifications, consent and quiet hours | **PARTIAL** | In-app notifications and console email exist; SMS is console-only. No consent model, quiet hours, template governance or health-data redaction policy. |
| 35 | Demo mode | **MISSING** | No `DEMO_MODE`, no permanent “no real payments or deliveries” banner and no startup refusal when demo mode is combined with the production profile. Seeding exists but is not a mode. |
| 36 | Release operations: accessibility, i18n, PWA, backups and monitoring | **PARTIAL** | CI, tests and dependency checks are strong. No axe/Lighthouse CI, i18n framework, service worker/manifest, backup restore drill or monitoring/alerting implementation. |

The 36 gates above are a reconstruction, not a verbatim copy of the unavailable original
Section 24. Exact sign-off remains blocked until the original lines are supplied.

## 5. Role flow results

### Customer

Verified:

- Register, verify email, login.
- Add address.
- Browse/search; suggestions returned.
- Add cart item.
- Place COD order.
- Cancel before preparation and release stock.
- View order, invoice data and tracking key.
- Upload a prescription; download own file.
- Review a delivered order.

Breaks / gaps:

- Order page returns no `deliveryOtp`; the only OTP found was in the notification body.
- Five failed logins do not lock the account.
- OTP request for an unknown email returns `404` (“No account found for this email”), disclosing account existence.
- The UI has no visible OTP on the order page even though the delivery page tells the customer to read it there.

### Pharmacy

Verified:

- Login and dashboard.
- List and open own orders.
- Accept, prepare and mark ready.
- Inventory list; set stock to 1; restore to 34.
- Low-stock and ledger paths exist.
- Review a prescription assigned to the pharmacy.

Breaks / gaps:

- `GET /pharmacy/orders/{id}/invoice` returned another pharmacy's invoice with `200`; the detail endpoint correctly returned `404`.
- `GET /pharmacy/prescriptions/{id}/reviews` returned another pharmacy's reviews with `200`.
- No separate pharmacist role or registration verification.
- No batch-aware dispensing; expiry does not reduce available stock.
- Admin approval does not require document verification.

### Delivery

Verified:

- Login/dashboard.
- Accept assignment.
- At pharmacy → picked up → out for delivery.
- Wrong OTP rejected; correct OTP delivered.
- WebSocket authorization tests pass.

Breaks / gaps:

- Customer cannot see the OTP in the order payload.
- Rider copy says six digits; backend sends four.
- No proof-of-delivery photo storage despite `proofSubmitted` flag.
- Live WebSocket tracking was unit-tested, not browser-driven in M0.

### Admin

Verified:

- Login, dashboard, order list and platform report.
- Force assignment to an online partner.
- Refund/audit/control endpoints exist.

Breaks / gaps:

- Unfiltered audit-log query returns `500`; filtered action query works.
- Admin can mark a delivered order refunded without a refund transaction.
- No live heatmap, command palette or SLA breach board.
- Settings changes are not audited.
- Admin approval of partner documents is a status change, not a document review.

### Public

Verified:

- Tracking by unguessable key.
- No street address or phone in the response.

## 6. Security audit

| Area | Finding | Severity | Evidence |
| --- | --- | --- | --- |
| Cross-tenant order data | Pharmacy 2 can read pharmacy 1's invoice by order ID. `PharmacyOrderController.invoice` calls `orderService.invoice(..., staff=true)` and `invoice()` fetches by order ID only. | **P0** | `GET /api/v1/pharmacy/orders/14/invoice` as pharmacy 2 → `200`; seller `MediDrop Demo Pharmacy — Salt Lake`; detail as same token → `404`. |
| Financial state bypass | Admin can transition `DELIVERED → REFUNDED` without `PaymentService.refund`. | **P0** | Admin PATCH returned `200 REFUNDED`; `/admin/refunds` count `0 → 0`. `OrderStateMachine` allows the edge. |
| Prescription review history | Pharmacy 2 can read pharmacy 1's review comments by prescription ID. | **P1** | `GET /pharmacy/prescriptions/1/reviews` as pharmacy 2 → `200`, one row. |
| Controlled medicines | No pharmacist role, restricted list, CDSCO schedule enforcement or `UNKNOWN → REQUIRED` rule. | **P0** | `Role.java`; `PrescriptionService.review`; V1 `schedule_class` is free text. |
| Auth enumeration | Unknown email OTP request returns account-specific `404`. | **P1** | Live `/auth/otp/request` with unknown email → `404`. |
| Brute force | Five bad passwords followed by a correct login still succeeds; limiter is per IP+path, not per account/device. | **P1** | Five `401`s then `200`; `RateLimitFilter` key. |
| Admin bootstrap | Known dev admin password; no forced rotation. | **P1** | `DemoDataSeeder`; `application-dev.yml`. |
| Secrets | Gitleaks: 11 commits, no leaks. No tracked `.env`; only `.env.example` files. | **PASS** | Gitleaks output; `git ls-files`. |
| Dependency risk | npm 0; Maven OSV 2 Spring WebMVC advisories. | **P2 residual** | OSV batch 101 queries; `docs/DEPENDENCIES` evidence in this file. |
| Webhook | Invalid signature rejected. | **PASS** | `POST /payments/webhook` with bad signature → `401`. |
| File upload | Content-type + magic-byte validation; private access checks on the file endpoint. | **PASS/PARTIAL** | Bad magic → `400`; customer B → `403`; pharmacy 2 → `403`. |
| Public URLs | Tracking key is opaque; response has no PII. | **PASS** | Live tracking response. |
| Audit integrity | Audit rows are append-only by convention, not by DB permissions; IP address is always null. | **P1** | `AuditService.record`; `AuditLog.ipAddress`; no DB trigger. |

### Secrets scan

- `gitleaks detect --source . --redact` in Docker: **11 commits scanned, no leaks found**.
- Fallback regex over full history for GitHub tokens, AWS keys, Stripe live keys and private-key headers: no matches.
- No real credentials are tracked. `.env.example` is a template; `frontend/.env.example` is also tracked by design.

### Dependency audit

- `npm audit`: **0** total.
- OSV Maven batch: **2** advisories, both `org.springframework:spring-webmvc:6.2.19`.
- Gitleaks and OSV were run because `gitleaks`/`osv-scanner` binaries were absent locally; Gitleaks ran from Docker and OSV was queried directly. `org.owasp:dependency-check-maven` was not run in M0 because its NVD bootstrap is not provisioned in this environment; the OSV result is the actionable Maven evidence.

## 7. Fake data, placeholders and “theatre” scan

| Check | Result |
| --- | --- |
| Hardcoded dashboard metrics | **None found.** Admin dashboard and report values come from repository queries; live dashboard returned `totalOrders=14`, `grossRevenue=5550.30`. |
| Mock API responses | **None found.** Sandbox payments, console email/SMS, Haversine maps and disabled OCR are explicit provider modes, not fake success in production. |
| Buttons without handlers | **No obvious dead handlers** in the inspected customer, pharmacy, delivery and admin pages; `href="#"` was not found. |
| `localStorage` critical state | Auth tokens and cached user are stored in `localStorage` (`frontend/src/lib/api.ts`, `store/auth.ts`). This is a documented stateless-SPA trade-off; it is still XSS-sensitive. |
| Random values | `Math.random()` appears only in the UI toast ID (`components/ui.tsx`), not in business data. |
| Hardcoded location | Home page falls back to Salt Lake, Kolkata when geolocation is unavailable; it is labelled “demo area”, not presented as live user location. |
| Demo flags | Only `medicines` and `prescriptions` carry `is_demo`. Seeded users, pharmacies, delivery partners, inventory, orders and reviews do not. `DemoDataSeeder`’s comment that every record is flagged is therefore inaccurate. |
| Seed volume | 15 medicines, 2 pharmacies, 2 delivery partners, 1 customer, 1 admin; not the addendum’s 12 pharmacies / 500 products / 6 customers / 40 orders. |
| Forecast label | `ReportingService.forecast` calls a simple 30-day average “weighted”; the label overstates the method. |
| Error/loading/empty states | Present on most inspected screens (`PageLoader`, `Alert`, `EmptyState`), but no automated screen-by-screen gate. |

## 8. Order state machine

The happy path is enforced and observed. The audit found these currently possible transitions that contradict the intended lifecycle:

| Transition | Current result | Why it matters |
| --- | --- | --- |
| `DELIVERED → REFUNDED` | Allowed for `ADMIN` and reachable via `PATCH /admin/orders/{id}/status` | Skips `REFUND_PENDING` and does not call `PaymentService.refund`; creates a refunded order with no refund ledger row. |
| `CANCELLED → REFUNDED` | Allowed for `ADMIN` | Same bypass. |
| `FAILED → REFUNDED` | Allowed for `ADMIN` | Same bypass; no refund record. |
| `REFUND_PENDING → FAILED` | Allowed for `ADMIN` | A refund attempt can be relabelled as a failed order without a provider outcome. |
| `PAYMENT_CONFIRMED → CANCELLATION_REQUESTED` | Allowed for `CUSTOMER` | Docs describe requests starting at `PREPARING`; this is more permissive than documented. |

Observed valid path:

`PAYMENT_CONFIRMED → PHARMACY_ACCEPTED → PREPARING → READY_FOR_PICKUP → DELIVERY_ASSIGNED → PICKED_UP → OUT_FOR_DELIVERY → DELIVERED`

No transition skipped a state in the happy path. The defect is at the terminal refund edges, not the fulfilment chain.

## 9. Inventory concurrency proof

Test:

1. Set pharmacy 1, inventory 2 (Dolo 650) stock to **1**.
2. Customer A and customer B each added one unit to separate carts.
3. Both placed COD orders at the same time with distinct idempotency keys.

Result:

- Customer A: **`200`**, order 15 created.
- Customer B: **`500`**, `An unexpected error occurred`.
- Final inventory: `stockQuantity=0`, `availableQuantity=0`.
- One order only: **overselling was prevented**.
- Cancel of the winner returned `200`; stock restored to 1, then reset to the original 34.

Root cause from the clean API log:

```text
ObjectOptimisticLockingFailureException: Row was updated or deleted by another transaction
(or unsaved-value mapping was incorrect): [com.medidrop.inventory.PharmacyInventory#2]
at com.medidrop.order.OrderService.place(OrderService.java:153)
```

`checkoutSnapshot` reads the inventory entity before `place()` calls `findForUpdate`; the losing persistence context still holds the stale version, so the pessimistic lock is acquired but the subsequent flush sees a version conflict. The correct outcome is a `409` with a user-readable “stock changed” message, or an atomic conditional decrement. This is a P1 reliability fix, not evidence of overselling.

## 10. Prioritized gaps

### P0 — unsafe or blocks real use

1. **Invoice IDOR.** Scope `PharmacyOrderController.invoice` to the authenticated pharmacy; never allow a pharmacy to request an arbitrary order.
2. **Refund state bypass.** Remove direct `DELIVERED/CANCELLED/FAILED → REFUNDED` admin edges. Refund must go through `PaymentService` and a provider/ledger record.
3. **Pharmacist identity.** Add a `PHARMACIST` role or a verified pharmacist profile with registration number, state council, pharmacy link and approval authority. Only a verified pharmacist may approve/dispense Rx items.
4. **Controlled medicines.** Implement schedule classification (H/H1/X), `UNKNOWN → REQUIRED`, restricted-product enforcement and legal review before any Rx sale.
5. **Customer handover OTP.** Return the current handover OTP on the customer's own order detail (when assigned and not verified) or provide an unmistakable in-order reveal. Align four vs six digit copy. The notification-only fallback is not sufficient for the visible flow.

### P1 — product quality and trust

1. `admin/audit-logs` unfiltered query returns `500`; fix the nullable `:action` JPQL binding and add a regression test.
2. Concurrency loser returns `500`; return `409` and test two-customer race.
3. Prescription review-history IDOR; scope by pharmacy/owner.
4. No DB `CHECK (price <= mrp)` or non-negative stock constraints.
5. Batch/expiry is bookkeeping only; no batch allocation, expiry exclusion or FEFO.
6. No per-account/device lockout; OTP account enumeration.
7. Admin bootstrap uses a known dev password; no rotation/one-time setup.
8. OTP UI uses `deliveryOtp: null`; rider copy says 6 digits; no proof-of-delivery storage.
9. No GST HSN columns, server-side invoice sequence or PDF.
10. No returns/damaged/near-expiry dispute flow.
11. No demo mode/banner/prod refusal.
12. No data pipeline, no `data_quality_issues`, no catalogue licence tracking.
13. Notifications are console/in-app; no real SMS, consent or quiet hours.
14. Seed data is not fully `is_demo`; 15 products only.
15. No backups/restore test, metrics, tracing or alerting implementation.

### P2 — wow factor and long-term quality

1. i18n (English/Hindi/Bengali), voice search and PWA shell.
2. axe/Lighthouse CI, keyboard audit, reduced-motion audit.
3. Admin live heatmap, command palette and SLA board.
4. Real OCR with editable chips and pharmacist seal.
5. Pulse Tracker route glow/ETA ring and live freshness indicator.
6. Verified Chain timeline and My Cabinet/refill prediction.
7. Better forecast model and evaluation harness.
8. Expanded Kolkata seed (12 pharmacies, 500 products, 40 orders) with demo flags.

## 11. Proposed milestone order after M0

The addendum's order is mostly correct, but M0 evidence requires some P0 work before the nominal M2 auth milestone.

| Milestone | Scope | Gate |
| --- | --- | --- |
| **M1** | Fix invoice IDOR, refund bypass, audit-log 500, concurrency 409, customer OTP projection; add regression tests. No new feature work. | All P0 security/finance/OTP tests pass; CI green. |
| **M2** | Pharmacist role and verification; admin bootstrap; per-account rate limits/lockout; OTP enumeration fix. | A non-pharmacist cannot approve Rx; a verified pharmacist can; abuse tests pass. |
| **M3** | Dataset licensing/pipeline, 500-product demo catalogue, typo search, 300-query golden set. | DATA_SOURCES complete; import report; top-3 ≥95%. |
| **M4** | Batch/FEFO, expiry exclusion, DB constraints, no-oversell 409, partial-fulfilment rules. | Concurrency and expiry tests pass. |
| **M5** | State machine cleanup, idempotency race, returns/disputes, GST HSN/PDF. | 100 order-state scenarios pass; direct refund impossible. |
| **M6** | Prescription review IDOR, verified pharmacist, OCR assist, retention. | Cross-tenant review/file tests pass; OCR never authorizes. |
| **M7** | Razorpay live sandbox certification, webhooks, refunds, COD rules. | Provider sandbox and replay tests pass. |
| **M8** | Maps/PostGIS, coverage, ETA, tracking. | Geocoding and radius tests pass. |
| **M9** | Delivery partner app, OTP UX, GPS reconnect, proof of delivery. | End-to-end delivery with visible OTP. |
| **M10** | Admin Control Tower, reports, settings, audit viewer, command palette. | No 500s; every mutation audited. |
| **M11** | Design tokens/component library, all four surfaces, i18n/PWA. | Screenshots at 390/1440, light/Ops Night, axe pass. |
| **M12** | Forecast, explainable allocation, search quality. | Offline eval + explainability. |
| **M13** | Security review, load test, backup restore, monitoring, deploy guide, legal review. | No launch claim until external legal/ops sign-off. |

## 12. What was not verified

- Live Razorpay charges, refunds and webhook delivery.
- Real SMTP/SMS delivery.
- Google geocoding.
- S3/object storage.
- OCR extraction.
- Browser-driven UI role flows, cross-browser visual regression, axe and Lighthouse.
- Load/soak testing.
- Production backup/restore, monitoring and alerting.
- Original Section 24 exact wording.
- Legal/compliance sign-off.

## 13. Audit artifacts

Commands and raw outputs were captured under `/tmp/medidrop-audit/` during the audit, including:

- `backend-test.log`
- `frontend-test.log`
- `frontend-build.log`
- `npm-audit.json`
- `osv-vulns.json`
- `gitleaks.log`
- `compose-valid.log`
- `compose-example.log`
- `results.json`
- `openapi8081.json`

These are local audit artifacts, not committed runtime data.
