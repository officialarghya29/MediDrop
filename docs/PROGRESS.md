# Progress

Last updated: 2026-11-20

Current milestone: **M1 — foundation and current-flow safety**

Status: **M1 safety slice complete; the 50-way stock gate is closed. One-command and design-token polish remain open.**

## Done

- Read the existing repository, docs, migrations, controllers, services, security configuration
  and CI.
- Audited against the **36-item Section 24 reconstruction** and the **Addendum v5 Part 8** gates.
- M0 backend baseline: **28 passed**; after the M1 safety slice, deep scan, substitute feature,
  fallback fix, 0.1.4 hardening, 0.1.5 provider-state checks, 0.1.6 edge hardening and 0.1.7
  measured-edge work: **52 passed**.
- Ran frontend tests: **13 passed**; Playwright + axe public, customer, console and role gate: **25 passed**.
- Ran the production frontend build on Vite 8.3.8: **passed**.
- Ran `npm audit`: **0 vulnerabilities**.
- Ran an OSV batch scan of 101 Maven runtime packages: **2 residual Spring WebMVC advisories**.
- Ran Gitleaks over 11 commits: **no leaks**.
- Validated both `docker compose config` and the documented `.env.example` path.
- Started a clean API on `:8081` and verified OpenAPI (96 KB, ~1.5 s).
- Exercised customer, pharmacy, delivery, admin and public tracking paths.
- Confirmed cross-tenant invoice read and prescription review-history read.
- Confirmed admin direct `DELIVERED → REFUNDED` bypass with no refund ledger row.
- Proved the one-unit/two-customer race creates only one order but returns a `500` to the loser.
- Confirmed invalid webhook signatures are rejected.
- Confirmed file magic-byte validation and customer/pharmacy file isolation.
- Confirmed sandbox UPI capture and COD fulfilment.
- Confirmed the v5 Part 4 engines and schema are absent: no node selection, ETA range/calibration,
  Rx Vault, Savings Engine, cold chain, rider dispatch, step events, DEMO_MODE, feature flags or
  ComplianceSignoff.
- Confirmed the M0 test stack was H2/JUnit + Vitest, not Testcontainers, with no coverage gate.
  0.1.7–0.1.8 later added Playwright/axe, Lighthouse CI, 250K and sustained-load harnesses.
- Wrote `docs/AUDIT.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`, `docs/RUNBOOK.md`,
  `docs/DATA_SOURCES.md`, `docs/COMPLIANCE.md`, `docs/PERF.md` and `docs/A11Y.md`.

## M1 progress (2026-11-20)

Completed and covered by tests:

- Pharmacy invoice reads are now scoped through `findByIdAndPharmacyId`; pharmacy 2 cannot read
  pharmacy 1's invoice. Customer invoices remain scoped by customer.
- Direct `DELIVERED/CANCELLED/FAILED → REFUNDED` transitions are removed. The admin status endpoint
  refuses refund states; `PaymentService` moves an order through `REFUND_PENDING` after the gateway
  and ledger record exist.
- Customer order detail reveals the current 4-digit handover OTP until verification; the rider copy
  and input now match four digits.
- Pharmacies need a verified pharmacist name, registration number and council before they can review
  prescriptions. Admin verification endpoint and audit action added. A separate `PHARMACIST` login
  role remains M2.
- Medicine schedules are constrained to `OTC/H/H1/X/UNKNOWN`; `H/H1/X/UNKNOWN` force prescription
  review, and `sales_blocked` is refused at checkout.
- PostgreSQL now enforces `price <= mrp`, positive prices and non-negative/consistent stock.
- Unfiltered `GET /admin/audit-logs` no longer binds a nullable action parameter; live PostgreSQL
  returned `200`.
- Checkout reads a projection before acquiring the inventory lock, so the stale managed entity cannot
  surface as a 500. Optimistic conflicts map to `409`.
- Fresh PostgreSQL 16 boot applied Flyway V1–V6, seeded 18 medicines, and served the catalogue.

Deep scan (2026-11-20):

- Pharmacy and delivery onboarding routes are explicitly public instead of falling through to `401`.
- Pharmacy registration no longer echoes the plaintext password.
- Prescription review history is scoped to the assigned pharmacy, with a cross-tenant regression.
- OTP request/verify no longer disclose whether an email has an account.
- Pharmacy documents are magic-byte checked, not just content-type checked.
- Delivery failure is audited as a delivery action and cannot complete from an impossible state.
- Duplicate phone registration is rejected for pharmacy and delivery accounts.
- Console SMS/email transports no longer log OTP or reset bodies.
- Admin setting changes are audited; refund path/body order IDs must match.
- Customer OTP is hidden once the order leaves an active delivery state.

Still open in M1:

- One-command developer entry point and shared design-token pass.
- Live gateway refund and browser-driven OTP handover.
- Admin UI for pharmacist verification and medicine sale blocks (API exists; UI is M2/M12).
- Per-account login lockout and distributed abuse controls are not implemented.
- `main` is now protected against force-push/deletion and requires the three CI jobs for pull
  requests; admin direct pushes remain available. Secret scanning/push protection and Dependabot
  alerts/security updates are enabled. Dependabot now ignores major-version jumps.

## Substitute comparison (0.1.2)

- Added `GET /medicines/{id}/substitutes`: lower-cost equivalents at a selected pharmacy.
- Safety filters require identical generic composition, strength, dosage form, pack size, prescription
  flag and schedule class; candidates must be in stock and strictly cheaper.
- Prescription candidates are shown with a mandatory pharmacist-review warning and are never added to
  the cart automatically.
- `MedicineSubstituteTest` covers the positive case, different-pack exclusion, Rx exclusion, blocked
  product exclusion, out-of-stock exclusion, savings calculation and the Rx warning.
- Live PostgreSQL check: Paracetamol 500 mg at ₹343 returned the 500 mg value pack at ₹249, a ₹94
  (27%) saving, at the same approved pharmacy. These are demo records, not production prices.

## Substitute reliability (0.1.3)

- When no pharmacy is supplied, the API now walks price-ordered stock and selects the cheapest
  pharmacy that is approved, active and accepting orders. A paused cheapest pharmacy can no longer
  hide a valid lower-cost match.
- `MedicineSubstituteTest.skipsAnUnavailableCheapestPharmacyWhenNoPharmacyIsRequested` covers it.
- Live PostgreSQL check: with Salt Lake paused, the API selected Bhowanipore and returned the ₹255
  value pack against ₹343 (₹88, 26%). Demo prices only.

## Model hardening (0.1.4)

- OTP verification checks account state before minting tokens: locked, disabled and deletion-pending
  accounts are refused. A successful code is consumed once; replay is rejected.
- OTP and password-reset token reads use pessimistic write locks so simultaneous requests cannot both
  consume a single-use token.
- `Order` has an optimistic version and PostgreSQL migration `V6` adds the column. Concurrent
  lifecycle writers now produce a recoverable conflict instead of a silent overwrite.
- A rejected rider triggers reassignment in `DELIVERY_ASSIGNED`; the order no longer stalls waiting
  for an impossible backwards transition.
- The console version label follows `0.1.4`; README, charts and release notes were rebuilt around the
  verified telemetry.

## Provider state authority (0.1.5)

- Delivery writes now require an `APPROVED` rider. A suspension that lands after assignment cannot
  be bypassed by accepting, moving or completing the job.
- Pharmacy transitions require an approved, active pharmacy. Cancellation remains available to close
  an existing order, but acceptance, preparation and handover do not.
- Prescription review, queue reads and file access require an active approved pharmacy, so suspension
  removes PHI access immediately.
- Inventory and catalogue maintenance require an active pharmacy, closing the stock-side bypass.
- `SuspendedAuthorityTest` covers the suspended-rider and suspended-pharmacy paths.

## 50-way stock gate (0.1.5)

- Added `infrastructure/scripts/stock-race-check.py`, a black-box harness that seeds 50 customers and
  carts, then fires 50 simultaneous checkouts at one inventory row with stock 10.
- Live PostgreSQL 16 result: **10 × 200, 40 × 409, 10 orders, stock 0, 10 SALE ledger units, 0 errors**.
- This closed the 50-way M1 evidence gap. 0.1.7 added the 250K search and public browser/axe gates;
  0.1.8 adds single- and two-instance search/order soaks plus customer and role axe coverage.
  production-network load and full keyboard/screen-reader coverage remain open.

## Sustained and authenticated gates (0.1.8)

- Added `infrastructure/scripts/soak-check.py`, a 60-second external gate with concurrent search
  workers and real COD order placement through the public HTTP flow. It accepts repeated base URLs.
- The required CI smoke job starts two API instances against one PostgreSQL database, then runs the
  soak across both after the 33-check smoke suite. It fails on any error, empty search, unused
  instance, non-confirmed order or p95 above target.
- Two-instance local run: **4,351 searches** (2,176 / 2,175 per node) at p95 **16.2 ms**, and **31
  cross-node orders** at p95 **99.7 ms**, zero errors, against 300 ms and 800 ms targets.
- Two-instance CI run `44780590024`: **4,883 searches at p95 13.1 ms** and **31 orders at p95 61.8
  ms**, zero errors.
- Extended `catalogue-load-check.py` to repeated base URLs and measured the 250K catalogue across two
  API nodes: **p50 189.3 ms, p95 286.5 ms, p99 293.4 ms, 0 errors**; slower node p95 **290.6 ms**.
- Single-instance soak remains recorded at **5,001 searches p95 7.9 ms** and **31 orders p95 28.7 ms**.
- Extended Playwright + axe to all customer surfaces and all nine console subpages using role-scoped
  DTO fixtures and seeded browser sessions. Result: **25 passed**, zero WCAG A/AA violations.
- Added a five-run Lighthouse runner and CI job. Local median: home **93**, search **92**, with
  accessibility **100** on both. Hosted CI measured **78 / 77** performance with accessibility and
  best-practices **100**, so CI enforces a documented 75 floor while the product target remains 90.
- Fixed six real defects without suppressing a rule: concurrent order-number collision, search
  heading order, unread-count accessible name, support-ticket contrast, inventory stock-label
  contrast, and keyboard access to the delivery-history scroll region.
- Limits: the soak runs against the 18-row demo catalogue, not 250K; Lighthouse covers only home and
  search; production-network load, keyboard, screen-reader, touch-target, Ops Night and i18n remain
  open.

## Measured edge (0.1.7)

- `catalogue-load-check.py` seeds 250,000 medicines plus one inventory row each and measures 100 real
  HTTP searches. Current result: **p50 189.1 ms, p95 280.5 ms, p99 286.8 ms, 0 errors** against the
  300 ms target on PostgreSQL 16.
- V7 replaced raw-column trigram indexes with lowercased expression indexes that match the actual
  search predicate, and added a prefix index for suggestions.
- Playwright and axe now run in the required frontend CI job: four public pages and a search-to-detail
  journey pass with zero WCAG A/AA violations at 390 px.
- The deferred minor/patch dependency batch is applied; production npm audit remains zero.
- Limits: one local 250K run, no soak, no Lighthouse, no role-dashboard axe coverage and no recorded
  keyboard/screen-reader pass.

## Edge hardening (0.1.6)

- Suspended pharmacies and riders lose realtime tracking/delivery subscriptions, not only REST write
  access.
- Prescription upload requires an approved, active pharmacy; an approved-but-inactive pharmacy can
  no longer receive new prescription data.
- Checkout requires both email and mobile verification.
- Graceful shutdown, liveness/readiness probes, Hikari leak detection and JDBC batching are active.
- CI audits the production npm tree. Dependabot majors are deferred, and `main` branch protection
  requires the three CI jobs for pull requests while preserving admin direct pushes.
- `SuspendedAuthorityTest`, `WebSocketAuthInterceptorTest` and `OrderSafetyTest` cover the new edges.

## Release checkpoint

`v0.1.4` added OTP identity guards, concurrent token locking, order versioning and rider
reassignment. `v0.1.5` enforced suspended-provider state and recorded the measured 50-way stock
result. `v0.1.6` closed realtime and inactive-pharmacy edges and added runtime/CI controls. `v0.1.7`
is the latest tagged **foundation release**: it measures 250K search, adds a Playwright/axe CI gate and
refreshes the deferred dependencies. The `0.1.8` mainline patch adds single- and two-instance
search/order soaks, 25 customer/console/role axe journeys, 250K two-node search and a Lighthouse CI
job. It passes 52 backend tests, 13 frontend tests, 25 browser/axe checks, 33 live smoke checks, the
two-instance soak, the 50-way stock gate and the 250K two-node search gate. It is not the M1
milestone tag and is not launch-ready; keyboard/screen-reader, touch-target, production-network load
and M2–M15 remain open.

## In progress

The M1 safety slice is implemented and tested; remaining M1 items above are intentionally not marked complete.

## Blocked

1. **Exact original Section 24.** The pasted addendum references 36 checkboxes but does not include
   their text, and the repository/history does not contain them. The audit uses a labelled 36-item
   reconstruction. Exact sign-off is blocked until the real list is supplied.
2. **Provider credentials.** Live Razorpay, SMTP, SMS, Google Maps, S3 and OCR cannot be certified
   without accounts/keys or a provider sandbox.
3. **Legal review.** Online pharmacy, Schedule H/H1/X, prescription retention, DPDP, GST,
   substitution, advertising/delivery-time claims and rider-welfare compliance require a qualified
   professional. No launch claim is allowed.
4. **Original Section 10.** The exact order-state specification was not supplied; the audit uses
   `docs/order-lifecycle.md` and the implemented `OrderStateMachine`.
5. **Production-scale and full accessibility environment.** The 250K two-node search, 60-second
   two-instance soak, 50-way stock race and mobile Lighthouse median now exist. Licensed data,
   production-network load, PostGIS/nearby-node queries, N+1 proof and keyboard/screen-reader passes
   are still NOT VERIFIED.

## Next

1. **M1 Foundation:** close the one-command run and design-token pass, then tag `v0.2.0-m1` after
   CI. The P0/P1 safety slice and 50-way stock gate are already implemented and tested.
2. **M2 Auth + RBAC:** five roles including verified Pharmacist, JWT/refresh, OTP interface, audit
   log, secure admin bootstrap, DEMO_MODE, feature flags and ComplianceSignoff.
3. Then follow the v5 order: M3 catalogue/data/search, M4 nodes/inventory, M5 orders, M6 Rx Vault,
   M7 payments, M8 node selection, M9 dispatch, M10 ETA/Verified Chain, M11 Savings, M12 admin,
   M13 cold chain/forecast, M14 design/i18n/PWA/a11y, M15 hardening and pilot review.

## Current tree state

M1 safety slice, provider-state hardening, the 0.1.7 measured-edge work and the 0.1.8 sustained /
authenticated gates. Application changes are the current-flow fixes listed above, order versioning in
Flyway V6, OTP/rider hardening, suspension checks and the search heading fix. The v5 engines, design
system, i18n, PWA, DEMO_MODE, feature flags and ComplianceSignoff remain unimplemented.
`.env.example` is unchanged because those flags are still not read or enforced.

## Audit DB notes

The local demo database was used for live tests. The following records are audit fixtures:

- Order 15: race winner cleanup; cancelled, stock restored.
- Order 16: full COD delivery, then deliberately moved to `REFUNDED` to prove the bypass.
- Orders 17 and 18: idempotency and sandbox-payment probes; cancelled.
- Prescription 1: uploaded/approved test image; review history fixture.
- Customer B (`audit.*@example.com`) and its address: test identity.

These are local development rows, not production data.
