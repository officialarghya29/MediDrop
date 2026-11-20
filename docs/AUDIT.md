# M0 repository audit — Addendum v5

- Audit date: 2026-11-20
- Repository: `officialarghya29/MediDrop` (public; visibility checked 2026-11-20)
- Branch: `main`
- Audited application commit: `0e9b76c79b39b1139f57681500448dbb55d4b9f8`
- Documentation baseline: `4a37d33f64b61b237e3796273e5961acc4609f16`
- Audit process: clean API process on `:8081`, PostgreSQL 16 on `:5433`, seeded demo database
- Application code changed in M0: **none**. This file and the other `docs/` memory files are the only M0 changes.

## 1. Scope and checklist basis

The supplied **Addendum v5** supersedes all earlier addenda. It requires M0 to audit the existing
repository against the 36 items in original Section 24 **plus** the Part 8 quality gates, and to
change no application code.

The 36 original Section 24 lines are still not included in the pasted addendum and were not found
in the repository or its Git history. The 36-row matrix in Section 4 is therefore a clearly
labelled reconstruction. Part 8 is audited separately in **Section 4A**, and the new v5 engines
and schema in **Section 4B**. Exact final sign-off remains **BLOCKED** until the original 36 lines
are supplied and reconciled.

The audit still applies the evidence rule: no item gets a status without a file path, command
result, test result or live HTTP result. This file and the other `docs/` memory files are the only
M0 changes; no Java, TypeScript, SQL, Docker or nginx application file was changed.

## 2. Verdict

**Not launch-ready, and not yet a v5 product.** The existing core is materially stronger than a
demo: PostgreSQL migrations, server-side pricing, JWT rotation, private prescription files, a
single order state machine, database-backed dashboards, a 33-check smoke suite and CI all work.
But v5's differentiated engines are absent, and Part 8 is mostly unproven.

M0 found **five current-flow P0 issues** that block real healthcare use:

1. A pharmacy can read another pharmacy's order invoice.
2. An admin can mark a delivered order `REFUNDED` without creating a refund or calling the gateway.
3. There is no separate, verified pharmacist role; the pharmacy owner account approves prescriptions.
4. Controlled/restricted medicine handling and CDSCO schedule classification are absent.
5. The customer-facing handover OTP is not returned on the order page (only in a notification), so
   the visible UI cannot complete delivery without leaving the order screen.

One additional **P0 pilot gate** is missing:

6. **The v5 pilot cannot be gated in code.** There is no `DEMO_MODE`, no DB-backed feature flags
   (`EXPRESS_ENABLED`, `RX_ENABLED`, `SUBSTITUTION_ENABLED`, `COLD_CHAIN_ENABLED`, `VAULT_ENABLED`)
   and no `ComplianceSignoff` workflow. A production-profile process can run without the required
   RX/substitution sign-off. This must close before Stage 0/1, not just before scale.

The v5 Node Selection, ETA, Rx Vault, Savings and cold-chain engines are **MISSING**, not merely
undocumented. They are recorded in Section 4B and must not be mistaken for existing capability.

The repository must not be described as launch-ready, legally compliant, production-certified or a
v5 Verified-Fast implementation until these gaps, external provider certification and legal review
are closed.

## 2A. M1 remediation status (2026-11-20)

The audit above remains the M0 baseline. This table records what changed during M1 and deliberately
separates “fixed” from “still open”.

| Finding | M1 status | Evidence |
| --- | --- | --- |
| Cross-tenant invoice | **FIXED** | `OrderService.pharmacyInvoice` resolves the pharmacy first and uses `findByIdAndPharmacyId`; `OrderSafetyTest` proves pharmacy 2 and customer B receive `NOT_FOUND`. |
| Direct refund transition | **FIXED** | Direct terminal edges removed; `adminTransition` refuses refund states; `PaymentService` uses `REFUND_PENDING` only after the gateway/ledger record. `OrderStateMachineTest` and `OrderSafetyTest` cover it. Live provider refund remains uncertified. |
| Pharmacist identity | **PARTIAL** | Verified pharmacist name, registration, council and admin verification endpoint are enforced by `PrescriptionService.review`; `PrescriptionAuthorityTest` proves an unverified owner is rejected. Separate `PHARMACIST` login role is M2. |
| Controlled medicines | **PARTIAL** | `OTC/H/H1/X/UNKNOWN` constraint, `UNKNOWN → REQUIRED`, `H/H1/X → REQUIRED`, `sales_blocked` checkout refusal and DB checks are implemented. A legal CDSCO list and admin UI are not. |
| Customer handover OTP | **FIXED** | Current code is stored on the order only until verification and returned by customer detail; rider UI now says 4 digits. Browser-driven handover is still unverified. |
| Pilot safety gate (`DEMO_MODE`, flags, signoff) | **MISSING** | Deliberately M2. Do not run a real RX/substitution pilot yet. |
| Unfiltered audit logs `500` | **FIXED** | Derived queries replace nullable JPQL; live PostgreSQL `200`. |
| Concurrency loser `500` | **FIXED** | Projection-before-lock, `409` mapping and the measured 50-way stock gate close the known race. |
| Price/MRP and stock DB constraints | **FIXED** | `V5__m1_safety_invariants.sql`; fresh PostgreSQL applied all six migrations (V1–V6). |

## 2B. Deep-scan remediation (2026-11-20)

The deep scan re-read the whole backend surface, not only the M1 diff. These defects were confirmed
and fixed with regressions; they do not make the repository launch-ready.

| Finding | Status | Evidence |
| --- | --- | --- |
| Pharmacy registration echoed the plaintext password in its JSON response. | **FIXED** | `PharmacyController.register` now returns `ApiResponse<Void>`; `AuthFlowTest.pharmacyRegistrationDoesNotEchoThePassword`. |
| Public pharmacy and delivery onboarding returned `401` because the catch-all rule required authentication. | **FIXED** | `SecurityConfig` explicitly permits `POST /api/v1/pharmacies/register` and `POST /api/v1/delivery/register`; the pharmacy response regression now exercises the public route. |
| Prescription review history was readable by any pharmacy that knew an ID. | **FIXED** | `PrescriptionService.reviews` resolves the caller's pharmacy and uses `findByIdAndPharmacyId`; `PrescriptionAuthorityTest.reviewHistoryIsScopedToTheAssignedPharmacy`. |
| OTP request/verify returned account-specific `404`s. | **FIXED** | `requestOtp` returns the same success envelope for unknown emails; `verifyOtp` returns a generic invalid-code `400`; `AuthFlowTest.otpEndpointsDoNotRevealWhetherAnAccountExists`. |
| Pharmacy licence documents trusted the client's content type. | **FIXED** | Shared `FileValidation.assertMagicBytes` now checks PDF/JPEG/PNG signatures before storage. |
| Delivery failure was routed through `adminTransition`, producing an admin audit actor, and completion accepted an impossible `PICKED_UP` state. | **FIXED** | `OrderService.deliveryFailure` uses `Role.DELIVERY`; `DELIVERY_ASSIGNED → FAILED` is a delivery edge; completion requires `OUT_FOR_DELIVERY`. |
| Pharmacy and delivery registration did not check duplicate phone numbers. | **FIXED** | Both services reject an existing phone before creating the account. |
| Console SMS/email transports logged OTP and reset bodies. | **FIXED** | Console transports log only recipient/subject and body length. |
| Admin setting changes had no audit row. | **FIXED** | `SETTING_UPDATED` is written on every setting mutation. |
| Refund path and body could name different orders. | **FIXED** | `AdminController.refund` rejects a mismatch. |
| Customer order detail could reveal an old OTP after the order closed. | **FIXED** | OTP is returned only for `DELIVERY_ASSIGNED/PICKED_UP/OUT_FOR_DELIVERY`. |
| Per-account login lockout / distributed abuse controls. | **OPEN** | The limiter is still per instance and per IP+path; brute-force resistance is not certified. |
| Separate `PHARMACIST` login role, secure admin bootstrap, `DEMO_MODE`, flags, `ComplianceSignoff`. | **OPEN (M2)** | Deliberately not started before the M2 gate. |

### 0.1.5 provider-state authority (2026-11-20)

This pass followed the 0.1.4 identity work and tested whether a suspended provider could keep acting
on work assigned before suspension.

| Finding | Status | Evidence |
| --- | --- | --- |
| A suspended rider could accept, advance or complete an existing assignment. | **FIXED** | `DeliveryService` now requires `ApprovalStatus.APPROVED` in `accept`, `updateStatus` and `complete`; `SuspendedAuthorityTest.suspendedRiderCannotAcceptAnAssignedDelivery`. |
| A suspended pharmacy could accept a forward order. | **FIXED** | `OrderService.pharmacyTransition` requires an approved, active pharmacy for every target except cancellation; `SuspendedAuthorityTest.suspendedPharmacyCannotAcceptAnExistingOrder`. |
| A suspended pharmacy could review prescriptions, list the review queue or read prescription files. | **FIXED** | Review, queue, review history and file reads now require an approved, active pharmacy. |
| Inventory and catalogue writes only checked approval status, not active state. | **FIXED** | `InventoryService.approvedPharmacy` now requires approval and `active=true`. |
| The 50-way stock-10 gate had no reproducible evidence. | **PASS** | `infrastructure/scripts/stock-race-check.py` fired 50 simultaneous checkouts: 10 × `200`, 40 × `409`, 10 orders, stock 0, 10 SALE units, 0 errors on PostgreSQL 16. |
| Branch protection, secret scanning and Dependabot controls. | **FIXED (0.1.6)** | `main` now blocks force-pushes/deletion and requires all three CI contexts for pull requests; admin direct pushes remain available. Secret scanning, push protection and Dependabot alerts/security updates are enabled. |

### 0.1.6 hardened edge (2026-11-20)

This pass tested the state that remains after suspension: realtime channels, prescription assignment
and production lifecycle controls.

| Finding | Status | Evidence |
| --- | --- | --- |
| A suspended pharmacy or rider could still subscribe to realtime tracking/delivery channels. | **FIXED** | `WebSocketAuthInterceptor` now requires an approved, active pharmacy for tracking and an approved rider for delivery/tracking; `WebSocketAuthInterceptorTest` covers both. |
| An approved-but-inactive pharmacy could be selected for a new prescription upload. | **FIXED** | `PrescriptionService.upload` requires approval **and** active state; `SuspendedAuthorityTest.suspendedPharmacyCannotBeSelectedForPrescriptionUpload`. |
| Checkout accepted one verified contact channel. | **FIXED** | `OrderService.place` requires both email and mobile verification; `OrderSafetyTest.orderRequiresBothEmailAndPhoneVerification`. |
| No graceful drain, orchestration probes, leak detection or JDBC batching. | **FIXED** | `server.shutdown=graceful`, 30 s lifecycle timeout, `/actuator/health/liveness` + `/readiness` UP, Hikari leak detection, `hibernate.jdbc.batch_size=30`. |
| CI had no production dependency audit gate. | **FIXED** | Frontend job runs `npm audit --omit=dev --audit-level=high`; current tree reports 0. |
| Dependabot opened eight major runtime upgrades with partial compatibility coverage. | **FIXED** | All eight closed with migration rationale; `.github/dependabot.yml` ignores semver-major and groups minor/patch. |
| `main` had no branch protection. | **FIXED** | Force-push/deletion blocked, three CI contexts required, conversation resolution enabled, admin bypass retained for direct pushes. |

Verification: **52 backend**, **13 frontend**, **33 live smoke**, 50-way stock gate (10 winners / 40
conflicts), health/liveness/readiness `UP`, `npm audit --omit=dev` 0.

### 0.1.4 model hardening (2026-11-20)

The second stabilization pass targeted identity and lifecycle races rather than adding surface area.

| Finding | Status | Evidence |
| --- | --- | --- |
| OTP verification could issue tokens for a locked/disabled account. | **FIXED** | `AuthService.verifyOtp` now checks enabled, locked and deletion-pending state before reading or consuming a code; `AuthFlowTest.lockedAccountCannotVerifyOtpOrReceiveTokens`. |
| OTP and password-reset tokens could be consumed twice by simultaneous requests. | **FIXED** | Both repositories expose a pessimistic-write lookup; `verifyOtp` and `resetPassword` use it inside their transaction. Replayed OTP is covered by the same auth regression. |
| Concurrent order transitions had no stale-write guard. | **FIXED** | `Order` carries `@Version`, `V6__order_version.sql` adds the column, and `OrderVersionTest` proves a stale write fails. |
| A rejected rider left the order assigned with no replacement path. | **FIXED** | `DeliveryService.autoAssign` now distinguishes initial assignment from reassignment and preserves `DELIVERY_ASSIGNED`; `DeliveryReassignTest`. |
| Console sidebar displayed a stale `v1.0` label. | **FIXED** | `Shell.tsx` reports `v0.1.4`. |
| 50-way stock race, live providers, full browser/a11y/performance gates. | **PARTIAL** | 50-way gate **PASS (0.1.6)**; public/role Playwright-axe, 250K search, 60-second soak and Lighthouse added by 0.1.8. Live providers, multi-instance load and full keyboard/screen-reader coverage remain open. |

### 0.1.7 measured edge (2026-11-20)

This pass converted two open performance/accessibility claims into repeatable gates and applied the
deferred dependency batch.

| Finding | Status | Evidence |
| --- | --- | --- |
| Search trigram indexes did not match the lowercased query, so PostgreSQL scanned the raw-name B-tree. | **FIXED** | `V7__catalogue_search_expressions.sql` replaces the raw indexes with lowercased expression indexes and adds a prefix index. `MedicineRepository.search` no longer wraps nullable columns in `coalesce`. |
| No 250K catalogue performance evidence. | **FIXED** | `catalogue-load-check.py` seeds 250,000 medicines + inventory rows and runs 100 HTTP searches: **p50 189.1 ms, p95 280.5 ms, p99 286.8 ms, 0 errors** against a 300 ms target. |
| No Playwright or axe gate in CI. | **FIXED (public pages)** | Playwright 1.65.1 + axe 4.14.0 in the required frontend job: home, search, medicine detail, sign-in and search-to-detail; **5 passed**, zero WCAG A/AA violations at 390 px. Role dashboards, keyboard, screen-reader and Lighthouse remain open. |
| Logo accessible name omitted its visible tagline. | **FIXED** | The logo now uses its visible wordmark/tagline as the accessible name; the axe rule was not suppressed. |
| Six deferred minor/patch dependency updates. | **APPLIED** | React Query 5.107.2, Zustand 4.5.7, TypeScript 5.9.3, Testing Library React 16.3.3, Autoprefixer 10.6.2, Springdoc 2.10.0. Backend 52 and frontend 13 tests pass. |

Limits: one local 250K run, no soak, no Lighthouse, no role-dashboard axe coverage, no recorded
keyboard/screen-reader pass. This is still not a launch-readiness claim.

### 0.1.8 sustained and authenticated gates (2026-11-20)

| Finding | Status | Evidence |
| --- | --- | --- |
| Order placement had no sustained-load or percentile evidence. | **FIXED** | `soak-check.py` ran three search workers plus real COD order placement for 60 seconds: **5,001 searches, p95 7.9 ms; 31 orders, p95 28.7 ms; 0 errors**. Targets are 300 ms and 800 ms. The gate now runs in the required CI smoke job. |
| Role dashboards and checkout had no automated axe coverage. | **FIXED** | Playwright/axe now checks checkout, pharmacy, delivery and admin with role-scoped fixtures. Browser suite is **9 passed**, zero WCAG A/AA violations. |
| Lighthouse performance and accessibility were unmeasured. | **MEASURED** | Lighthouse 12.8.2 mobile, three-run median: home **92 performance / 100 accessibility**, search **93 / 100**. One home sample scored 82, so this is a median with recorded variance, not a CI floor. |
| Search results skipped a heading level. | **FIXED** | Lighthouse `heading-order` found h1 → h3. A hidden h2 now labels the results; no rule was disabled. |

Limits: the soak uses the 18-row demo catalogue and a single API/database node. Lighthouse covers the
public home and search only, not checkout or consoles. Keyboard, screen-reader, touch-target, Ops Night,
i18n and 1440 px audits remain open.

### GitHub controls (reviewed 2026-11-20)

| Control | State |
| --- | --- |
| Repository visibility | **Public** |
| Open issues / pull requests | **0 / 0** |
| Branch protection on `main` | **Enabled** — no force-push/deletion; three CI contexts required for PRs; admin direct push retained |
| Secret scanning / push protection | **Enabled** (2026-11-20) |
| Non-provider secret patterns / validity checks | **Disabled** (unavailable on this plan) |
| Dependabot alerts / security updates | **Enabled** (2026-11-20) |
| Dependabot version updates | **Minor/patch only**; semver-major ignored in `.github/dependabot.yml` |
| Latest CI on `main` | Green for the 0.1.7 release commit; 0.1.8 adds the soak step |

The public visibility exposes the source and this audit, including the historical P0 descriptions. No
token or `.env` file is tracked, but a public security audit is still a disclosure decision. Branch
protection was enabled after an explicit review of the direct-push workflow. Force-push and deletion
are blocked, all three CI contexts are required for pull requests, and admin bypass remains available
for this single-maintainer release rail.

## 3. Environment, build and dependency evidence

| Check | Command | Result |
| --- | --- | --- |
| Backend tests (M0) | `cd backend && mvn -B -ntp test` | **28 passed, 0 failed** |
| Backend tests (deep scan) | `cd backend && mvn -B -ntp test` | **37 passed, 0 failed** |
| PostgreSQL migration | fresh PostgreSQL 16, Flyway V1–V5 | **5 migrations successful; app started; 15 seeded products** |
| Frontend tests | `cd frontend && npm test` | **13 passed, 0 failed** |
| Frontend production build | `cd frontend && npm run build` | **passed** (1819 modules, Vite 8.3.8) |
| npm audit | `npm audit --json` | **0 vulnerabilities** at every severity |
| Maven OSV batch | 101 runtime packages queried against `api.osv.dev` | **2 advisories**, both `spring-webmvc 6.2.19` |
| Secret history | Gitleaks Docker, 11 commits | **no leaks found** |
| Compose validation | `docker compose config` with test secrets | **exit 0** |
| Compose from example | `docker compose --env-file .env.example config` with required secrets added | **exit 0** |
| nginx syntax | previous M0 infrastructure run (`nginx -t` in Docker) | **passed** |
| OpenAPI after clean boot | `GET /v3/api-docs` | **200**, 96,425 bytes, ~1.5 s |
| Part 8 harness | repository search + `frontend/package.json` + `.github/workflows/ci.yml` | **PARTIAL**: 0.1.8 has public/authenticated Playwright/axe, 250K search and a 60-second soak; still no Testcontainers, Lighthouse CI threshold or coverage gate |
| v5 engines/schema | search for `FulfilmentNode`, `EtaPrediction`, `RxVault`, `Substitution`, `FeatureFlag`, `ComplianceSignoff`, `requiresColdChain`, `OrderStepEvent` | **NO IMPLEMENTATION** found outside docs and generic settings |
| CI at audited commit | GitHub Actions run `44698246524` | **3/3 jobs successful** |
| CI at docs baseline | GitHub Actions run `44703376130` on `4a37d33` | **3/3 jobs successful** |

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

> Operational trap found during M0: rebuilding `target/medidrop-api-*.jar` while a JVM is running from that jar corrupts lazy class loading (`NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy`) and creates misleading timeouts. Stop the JVM before rebuilding, then start a clean process. The audit used a fresh `:8081` process for this reason.

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
| 36 | Release operations: accessibility, i18n, PWA, backups and monitoring | **PARTIAL** | CI, tests and dependency checks are strong. Public Playwright/axe runs in CI; Lighthouse, i18n, service worker/manifest, backup restore drill and monitoring/alerting remain absent. |

The 36 gates above are a reconstruction, not a verbatim copy of the unavailable original
Section 24. Exact sign-off remains blocked until the original lines are supplied.

## 4A. Addendum v5 Part 8 quality gates

These gates are additional to the 36 original Section 24 items. **NOT VERIFIED** means the
repository has no test or environment that can prove the claim; it is not a pass.

| # | Part 8 gate | Status | Evidence |
| --- | --- | --- | --- |
| P8-1 | Negative authorization tests for every cross-account access; rider never sees Rx content; only verified pharmacists approve Rx/substitution | **PARTIAL** | 28 backend tests and live RBAC checks exist. Live audit found two cross-tenant reads (invoice, review history) and no separate pharmacist role. Rider Rx visibility was not exercised. |
| P8-2 | Table-driven order state machine; every illegal transition rejected; history written | **BROKEN** | `OrderStateMachine` is an `EnumMap`, but explicitly allows `DELIVERED → REFUNDED`, `CANCELLED → REFUNDED` and `FAILED → REFUNDED`. Live admin PATCH produced `200 REFUNDED` with no refund row. History is written. |
| P8-3 | 50 parallel checkouts on stock 10 = exactly 10 successes | **PASS (0.1.6)** | `stock-race-check.py`: 10 × `200`, 40 × `409`, 10 orders, stock 0, 10 SALE units, 0 errors on PostgreSQL 16. |
| P8-4 | Payments: tampered signature rejected; duplicate webhook once; client success never trusted; amount equals server total | **PARTIAL** | `PaymentSignatureTest`; live invalid webhook → `401`; totals are server-calculated. Duplicate webhook replay, provider refunds and live Razorpay were not certified. |
| P8-5 | Rx/UNKNOWN items can never reach `DELIVERY_ASSIGNED` without pharmacist approval | **MISSING** | `Medicine.prescriptionRequired` is a boolean and defaults false; no `rx_status=UNKNOWN` model, schedule enforcement or approval gate query. The existing review gate is order-level, not item-level. |
| P8-6 | Security: gitleaks history clean; no high/critical dependencies; login/OTP/search/upload limits; upload sniffing; signed URLs ≤5 min; PII-redacted logs; no health details in push/SMS | **PARTIAL** | Gitleaks 11 commits clean; npm 0 and now gated by `npm audit --omit=dev --audit-level=high` in CI; Maven OSV still reports 2 Spring WebMVC advisories. Rate limit is per IP+path, not per account/device. Upload magic-byte check passed. Storage is local private, not signed URLs. Log redaction and notification redaction are not proven. |
| P8-7 | Performance targets on a 250K-product seed; no N+1 | **PARTIAL** | Search p95 **280.5 ms** on 250,000 medicines, 100 requests, PostgreSQL 16. Other targets and N+1 query-count proof remain open; see `docs/PERF.md`. |
| P8-8 | UX: loading/empty/error/success on every screen; inline validation; 44px targets; axe zero serious/critical; 390/1440 screenshots | **PARTIAL** | Public pages pass Playwright/axe WCAG A/AA at 390 px. Role dashboards, 1440 px screenshots, Lighthouse, keyboard, touch targets and i18n remain unverified. See `docs/A11Y.md`. |
| P8-9 | Reliability: Maps down → manual address + distance ETA; payments down → retry + cart kept; WebSocket reconnect; idempotency; backups with tested restore | **PARTIAL** | Haversine fallback returns distance and refuses geocoding; cart/order idempotency is sequential only; WebSocket auth tests exist but reconnect was not tested; no backup restore drill or payment retry test. |
| P8-10 | Testing: Testcontainers (Postgres/PostGIS/Redis); Playwright journeys + failures + Express + substitution; CI blocks on lint/types/tests/E2E/audit/axe; 80% coverage target | **PARTIAL** | Backend tests use H2, not Testcontainers. CI now runs public Playwright/axe and blocks on it, but role journeys, failure paths, Express/substitution browser coverage and a coverage threshold are missing. |
| P8-11 | Rider safety: no punitive timers; batching limits; cold-chain exclusion; safety pause | **MISSING** | No rider batch, dispatch engine, cold-chain flag or safety pause. Admin assigns manually; rider UI shows route only. |
| P8-12 | Demo/pilot gates: permanent DEMO banner; prod refuses `DEMO_MODE=true`; RX/substitution require ComplianceSignoff; kill switches per node/global | **MISSING** | No `DEMO_MODE`, feature flags, signoff table or node/global kill switch. |

## 4B. Addendum v5 engine and schema coverage

| v5 capability | Status | Evidence |
| --- | --- | --- |
| Fulfilment node model and `NodeStats` | **MISSING** | `Pharmacy` has radius, `avgPrepMinutes`, `acceptingOrders` and rating. It has no capacity/hour, cold-chain flag, reliability stats or node health. |
| Node Selection Engine (4.1) | **MISSING** | No ranked-node service, eligibility filters, configurable weights or reason codes. Cart/order currently resolve a single pharmacy. |
| ETA Engine (4.2) | **MISSING** | `HaversineMapsService` computes `distance / 22 km/h` and a point estimate. No range, prep-time median, calibration, prediction log or ETA error metric. |
| Rider dispatch (4.3) | **PARTIAL** | Admin force-assignment and rider status transitions exist. No pre-assignment on node acceptance, direction batching, shift caps or safety pause. |
| Rx Vault (4.4) | **MISSING** | `Prescription` has file, review and OCR fields only. No prescriber details, validity window, allowed refills, refills used or fast-path refill. |
| Savings Engine (4.5) | **MISSING** | No active-ingredient model, candidate generation, exclusions, substitution proposal, pharmacist approval or consent record. |
| Cold chain (4.7) | **MISSING** | No `requires_cold_chain` field, cold-chain node capability, insulated-bag flow or batching exclusion. |
| Feature flags and ComplianceSignoff (4.8) | **MISSING** | `system_settings` is a generic public key/value table. It has no audited feature-flag semantics, signoff record or fail-closed switch. |
| Step-event instrumentation (0.5) | **MISSING** | `order_status_history` records status changes, not the timestamped accept/pack/assign/pickup/handover funnel. `OrderStepEvents` does not exist. |
| v5 schema additions | **MISSING** | No `FulfilmentNodes`, `NodeStats`, `EtaPredictions`, `OrderStepEvents`, `SubstitutionProposals`, `SubstitutionConsents`, `RxVault`, `RiderBatches`, `FeatureFlags`, `ComplianceSignoffs`, `data_quality_issues`, `rx_status` or `requires_cold_chain`. |
| Verified Chain, Pulse Tracker, Savings Compare, Live Ops Board, Control Tower | **MISSING** | Current UI has order status history, a basic map and dashboards. It has no v5 signature components or live ETA ring. |
| i18n / PWA / Ops Night | **MISSING** | No i18n library, locale files, web manifest, service worker or dark Ops mode. |

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
- Settings changes are now audited; pharmacy/delivery document review remains status-only.
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
| Prescription review history | Pharmacy 2 could read pharmacy 1's review comments by prescription ID. | **FIXED** | `PrescriptionService.reviews` now resolves the owner's pharmacy and `findByIdAndPharmacyId`; regression added. |
| Controlled medicines | No pharmacist role, restricted list, CDSCO schedule enforcement or `UNKNOWN → REQUIRED` rule. | **P0** | `Role.java`; `PrescriptionService.review`; V1 `schedule_class` is free text. |
| Auth enumeration | Unknown email OTP request/verify returned account-specific `404`. | **FIXED** | Generic success/`400` envelopes; regression added. |
| Brute force | Five bad passwords followed by a correct login still succeeds; limiter is per IP+path, not per account/device. | **P1** | Five `401`s then `200`; `RateLimitFilter` key. |
| Admin bootstrap | Known dev admin password; no forced rotation. | **P1** | `DemoDataSeeder`; `application-dev.yml`. |
| Secrets | Gitleaks: 11 commits, no leaks. No tracked `.env`; only `.env.example` files. | **PASS** | Gitleaks output; `git ls-files`. |
| Dependency risk | npm 0; Maven OSV 2 Spring WebMVC advisories. | **P2 residual** | OSV batch 101 queries; `docs/DEPENDENCIES` evidence in this file. |
| Webhook | Invalid signature rejected. | **PASS** | `POST /payments/webhook` with bad signature → `401`. |
| File upload | Prescription and pharmacy-document uploads validate content-type, size and magic bytes; private access checks on the file endpoint. | **PASS** | Bad magic → `400`; customer B → `403`; pharmacy 2 → `403`. |
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

1. **Invoice IDOR.** Scope `PharmacyOrderController.invoice` to the authenticated pharmacy; never
   allow a pharmacy to request an arbitrary order.
2. **Refund state bypass.** Remove direct `DELIVERED/CANCELLED/FAILED → REFUNDED` admin edges.
   Refund must go through `PaymentService` and a provider/ledger record.
3. **Pharmacist identity.** Add a `PHARMACIST` role or verified pharmacist profile with registration
   number, state council, pharmacy link and approval authority. Only a verified pharmacist may
   approve/dispense Rx items or substitutions.
4. **Controlled medicines.** Implement schedule classification (H/H1/X), `UNKNOWN → REQUIRED`,
   restricted-product enforcement and legal review before any Rx sale.
5. **Customer handover OTP.** Return the current handover OTP on the customer's own order detail
   (when assigned and not verified) or provide an unmistakable in-order reveal. Align four vs six
   digit copy.
6. **Pilot safety gate.** Add `DEMO_MODE`, DB-backed feature flags, `ComplianceSignoff`, per-node and
   global kill switches. Until this exists, do not enable RX/substitution/Express in any real pilot.
   This closes in M2, but blocks Stage 0/1.

### P1 — product quality and trust

1. `admin/audit-logs` unfiltered query returns `500`; fix the nullable `:action` JPQL binding and add
   a regression test.
2. Concurrency loser returns `500` — **fixed**; the 50-way stock-10 gate and conditional decrement
   path are now covered in 0.1.5.
3. Prescription review-history IDOR — **fixed** by pharmacy scoping and a regression test.
4. No DB `CHECK (price <= mrp)` or non-negative stock constraints.
5. Batch/expiry is bookkeeping only; no batch allocation, expiry exclusion or FEFO.
6. No per-account/device lockout. OTP account enumeration is fixed; brute-force resistance remains open.
7. Admin bootstrap uses a known dev password; no rotation/one-time setup.
8. OTP UI uses `deliveryOtp: null`; rider copy says 6 digits; no proof-of-delivery storage.
9. No GST HSN columns, server-side invoice sequence or PDF; returns/damaged/near-expiry disputes absent.
10. No data pipeline, `data_quality_issues`, 250K catalogue or licence tracking.
11. No Node Selection, ETA range/calibration, Rx Vault, Savings Engine, cold chain or step events.
12. No signed URLs, i18n, PWA shell, Ops Night mode or Verified Chain/Pulse UI.
13. Notifications are console/in-app; no real SMS, consent, quiet hours or health-data redaction.
14. Seed data is not fully `is_demo`; 15 products only, not 12 nodes / 500 products / 40 orders.
15. No backups/restore test, metrics, tracing, alerting or incident playbook.
16. No Testcontainers, Lighthouse or coverage gate. Public Playwright/axe and a 250K search harness now exist.

### P2 — wow factor and long-term quality

1. Live ETA ring and glowing Pulse Tracker, with location freshness and honest ranges.
2. Verified Chain timeline on every order; Savings Compare with explicit Decline.
3. Rx Scan chips, pharmacist seal and My Cabinet expiry/refill rings.
4. Pharmacy Live Ops Board with sound/visual alerts and pick-pack scan flow.
5. Admin Control Tower heatmap, command palette and node health.
6. Better forecast model, ETA v2, pre-positioning suggestions and Kolkata-wide demo seed.
7. Accessibility/keyboard polish, reduced-motion and contrast audits.
8. Voice search and Bengali/Hindi transliteration quality.

## 11. Proposed v5 milestone order after M0

The v5 order is retained, with the M0 findings made explicit. The two documentation-only
requirements from M0 are complete; no milestone below has been started.

| Milestone | Scope | Gate |
| --- | --- | --- |
| **M1** | Foundation: close current-flow P0 1–5, plus the audit-log and concurrency P1 blockers; one-command local run; CI green; secret cleanup; design tokens and shared components. | Invoice/refund/OTP/auth tests pass; `docker compose up` works; CI green. |
| **M2** | Auth + RBAC: Customer, Pharmacy owner, Pharmacist, Rider, Admin; JWT/refresh; OTP interface; audit log; secure admin bootstrap; DEMO_MODE, feature flags and ComplianceSignoff. | Non-pharmacist cannot approve; pilot flags fail closed; prod refuses DEMO_MODE. |
| **M3** | Catalogue + v5 data pipeline, 250K-capable search, typo tolerance, admin categories. | Licence log complete; import report; top-3 ≥95%; search p95 measured. |
| **M4** | Pharmacy/node onboarding, licence verification, inventory batch/expiry/audit, no oversell, scan pick/pack. | 50-way stock-10 test; expiry/FEFO; licensed-node gate. |
| **M5** | Cart, checkout, backend pricing, state machine, idempotency, step-event instrumentation. | 100 state scenarios; no direct refund; step funnel complete. |
| **M6** | Prescription upload, Rx Vault, pharmacist queue, OCR assist, private/signed access. | Verified pharmacist approval; vault validity/refills; cross-tenant tests. |
| **M7** | Razorpay create/signature/webhooks/duplicate protection/refunds/COD. | Tampered/replayed webhook; amount match; sandbox certification. |
| **M8** | Maps + PostGIS + Node Selection Engine + coverage. | Nearby p95 <200 ms; explainable reason codes; fallback tested. |
| **M9** | Rider app + dispatch + live tracking + OTP + reconnect. | No punitive timer; batching/cold-chain rules; reconnect test. |
| **M10** | ETA v1 + Pulse Tracker + Verified Chain UI. | Range shown; predicted-vs-actual logged; freshness badge. |
| **M11** | Savings Engine + Savings Compare + consent + safety set. | 100% exclusion-set pass; no automatic substitution. |
| **M12** | Admin Control Tower, reports, settings, audit viewer, node health. | No 500s; every mutation audited; live health view. |
| **M13** | Cold chain, demand forecast, pre-positioning, ETA v2. | Cold-chain exclusion and capability tests; ETA MAE/p90 reported. |
| **M14** | Design polish, i18n, PWA, accessibility. | 390/1440 screenshots; axe zero serious/critical; Lighthouse ≥90. |
| **M15** | Hardening: security review, load test, backups/restore, monitoring, deploy guide, pilot readiness. | Part 8 + original 36 VERIFIED; go/no-go table; legal/ops sign-off. |

## 12. What was not verified

- Live Razorpay charges, refunds and webhook delivery.
- Real SMTP/SMS delivery.
- Google geocoding.
- S3/object storage.
- OCR extraction.
- Cross-browser visual regression, remaining page-level axe coverage and full keyboard/screen-reader audits.
- Multi-instance load, 250K-with-soak combination and PostGIS/nearby-node performance.
- Production backup/restore, monitoring and alerting.
- Original Section 24 exact wording.
- Legal/compliance sign-off.
- Addendum v5 Node Selection, ETA, Rx Vault, Savings Engine, cold chain, rider dispatch, step events,
  DEMO_MODE, feature flags and ComplianceSignoff.
- Multi-instance load, Lighthouse CI thresholds, full page/keyboard/screen-reader audits,
  390/1440 screenshots, Testcontainers and a coverage threshold.
- Live node capacity, cold-chain capability, rider batching and ETA backtest data.

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
