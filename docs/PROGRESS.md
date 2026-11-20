# Progress

Last updated: 2026-11-20

Current milestone: **M1 — foundation and current-flow safety**

Status: **M1 safety slice complete; the 50-way stock gate is closed. One-command and design-token polish remain open.**

## Done

- Read the existing repository, docs, migrations, controllers, services, security configuration
  and CI.
- Audited against the **36-item Section 24 reconstruction** and the **Addendum v5 Part 8** gates.
- M0 backend baseline: **28 passed**; after the M1 safety slice, deep scan, substitute feature,
  fallback fix, 0.1.4 hardening and 0.1.5 provider-state checks: **45 passed**.
- Ran frontend tests: **13 passed**.
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
- Confirmed the test stack is H2/JUnit + Vitest, not Testcontainers + Playwright + axe + Lighthouse,
  and there is no coverage or 250K-product performance gate.
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
- GitHub `main` protection remains disabled; it changes the direct-push workflow and needs an
  explicit decision. Secret scanning/push protection and Dependabot alerts/security updates are now
  enabled, and Dependabot version updates are configured.

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
- This closes the previously open 50-way M1 evidence gap. The 250K catalogue, sustained load and
  browser/accessibility gates remain open.

## Release checkpoint

`v0.1.4` added OTP identity guards, concurrent token locking, order versioning and rider
reassignment. `v0.1.5` is the current **foundation release**: it enforces suspended-provider state,
protects prescription data after suspension and records the measured 50-way stock result. It passes
45 backend tests, 13 frontend tests and the production build. It is not the M1 milestone tag and is
not launch-ready; one-command/design-token polish and M2–M15 remain open.

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
5. **250K catalogue and performance environment.** No licensed dataset, sustained-load/PostGIS
   environment or Lighthouse/axe run exists. The 50-way stock harness now exists, but the v5
   performance and accessibility targets are still NOT VERIFIED, not passed.

## Next

1. **M1 Foundation:** close the one-command run and design-token pass, then tag `v0.2.0-m1` after
   CI. The P0/P1 safety slice and 50-way stock gate are already implemented and tested.
2. **M2 Auth + RBAC:** five roles including verified Pharmacist, JWT/refresh, OTP interface, audit
   log, secure admin bootstrap, DEMO_MODE, feature flags and ComplianceSignoff.
3. Then follow the v5 order: M3 catalogue/data/search, M4 nodes/inventory, M5 orders, M6 Rx Vault,
   M7 payments, M8 node selection, M9 dispatch, M10 ETA/Verified Chain, M11 Savings, M12 admin,
   M13 cold chain/forecast, M14 design/i18n/PWA/a11y, M15 hardening and pilot review.

## Current tree state

M1 safety slice, 0.1.5 provider-state hardening and documentation. Application changes are the
current-flow fixes listed above, order versioning in Flyway V6, OTP/rider hardening and suspension
checks. The v5
engines, design system, i18n, PWA, DEMO_MODE, feature flags and ComplianceSignoff remain
unimplemented. `.env.example` is unchanged because those flags are still not read or enforced.

## Audit DB notes

The local demo database was used for live tests. The following records are audit fixtures:

- Order 15: race winner cleanup; cancelled, stock restored.
- Order 16: full COD delivery, then deliberately moved to `REFUNDED` to prove the bypass.
- Orders 17 and 18: idempotency and sandbox-payment probes; cancelled.
- Prescription 1: uploaded/approved test image; review history fixture.
- Customer B (`audit.*@example.com`) and its address: test identity.

These are local development rows, not production data.
