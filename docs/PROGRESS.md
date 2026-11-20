# Progress

Last updated: 2026-11-20

Current milestone: **M1 — foundation and current-flow safety**

Status: **M1 safety slice complete; remaining M1 polish and the 50-way gate are open.**

## Done

- Read the existing repository, docs, migrations, controllers, services, security configuration
  and CI.
- Audited against the **36-item Section 24 reconstruction** and the **Addendum v5 Part 8** gates.
- M0 backend baseline: **28 passed**; after the M1 safety slice: **33 passed**.
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
- Fresh PostgreSQL 16 boot applied Flyway V1–V5, seeded 15 medicines, and served the catalogue.

Still open in M1:

- 50-way stock-10 concurrency test and measured result.
- One-command developer entry point and shared design-token pass.
- Live gateway refund and browser-driven OTP handover.
- Admin UI for pharmacist verification and medicine sale blocks (API exists; UI is M2/M12).

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
5. **250K catalogue and performance environment.** No licensed dataset, PostGIS instance, 50-way
   checkout harness or Lighthouse/axe run exists. The v5 performance and accessibility targets are
   therefore NOT VERIFIED, not passed.

## Next

1. **M1 Foundation:** close the 50-way stock gate, one-command run and design-token pass, then tag
   `v0.2.0-m1` after CI. The P0/P1 safety slice is already implemented and tested.
2. **M2 Auth + RBAC:** five roles including verified Pharmacist, JWT/refresh, OTP interface, audit
   log, secure admin bootstrap, DEMO_MODE, feature flags and ComplianceSignoff.
3. Then follow the v5 order: M3 catalogue/data/search, M4 nodes/inventory, M5 orders, M6 Rx Vault,
   M7 payments, M8 node selection, M9 dispatch, M10 ETA/Verified Chain, M11 Savings, M12 admin,
   M13 cold chain/forecast, M14 design/i18n/PWA/a11y, M15 hardening and pilot review.

## Current tree state

M1 safety slice plus documentation. The only application changes are the current-flow fixes listed
above and Flyway V5; the v5 engines, design system, i18n, PWA, DEMO_MODE, feature flags and
ComplianceSignoff remain unimplemented. `.env.example` is unchanged because those flags are still
not read or enforced.

## Audit DB notes

The local demo database was used for live tests. The following records are audit fixtures:

- Order 15: race winner cleanup; cancelled, stock restored.
- Order 16: full COD delivery, then deliberately moved to `REFUNDED` to prove the bypass.
- Orders 17 and 18: idempotency and sandbox-payment probes; cancelled.
- Prescription 1: uploaded/approved test image; review history fixture.
- Customer B (`audit.*@example.com`) and its address: test identity.

These are local development rows, not production data.
