# Progress

Last updated: 2026-11-20

Current milestone: **M0 — repository audit (Addendum v5)**

Status: **M0 complete, awaiting approval. No application code changed.**

## Done

- Read the existing repository, docs, migrations, controllers, services, security configuration
  and CI.
- Audited against the **36-item Section 24 reconstruction** and the **Addendum v5 Part 8** gates.
- Ran backend tests: **28 passed**.
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

## In progress

None. M0 stops here per the addendum.

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

After the user says **go**:

1. **M1 Foundation:** fix current-flow P0 1–5 (invoice tenancy, refund bypass, handover OTP, controlled-medicine
   enforcement, pharmacist identity) plus the audit-log 500 and concurrency 409 P1s, with regression tests,
   one-command run, CI and shared design tokens/components.
2. **M2 Auth + RBAC:** five roles including verified Pharmacist, JWT/refresh, OTP interface, audit
   log, secure admin bootstrap, DEMO_MODE, feature flags and ComplianceSignoff.
3. Then follow the v5 order: M3 catalogue/data/search, M4 nodes/inventory, M5 orders, M6 Rx Vault,
   M7 payments, M8 node selection, M9 dispatch, M10 ETA/Verified Chain, M11 Savings, M12 admin,
   M13 cold chain/forecast, M14 design/i18n/PWA/a11y, M15 hardening and pilot review.

## Current tree state

M0 documentation only. No Java, TypeScript, SQL, Docker or nginx application change was made
during the audit. The `.env.example` files are unchanged because the v5 feature flags and demo mode
are not implemented yet; documenting non-existent variables would be fake capability.

## Audit DB notes

The local demo database was used for live tests. The following records are audit fixtures:

- Order 15: race winner cleanup; cancelled, stock restored.
- Order 16: full COD delivery, then deliberately moved to `REFUNDED` to prove the bypass.
- Orders 17 and 18: idempotency and sandbox-payment probes; cancelled.
- Prescription 1: uploaded/approved test image; review history fixture.
- Customer B (`audit.*@example.com`) and its address: test identity.

These are local development rows, not production data.
