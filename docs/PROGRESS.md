# Progress

Last updated: 2026-11-20

Current milestone: **M0 — repository audit**

Status: **M0 complete, awaiting approval. No application code changed.**

## Done

- Read the existing repository, docs, migrations, controllers, services, security configuration and CI.
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
- Wrote `docs/AUDIT.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`, `docs/RUNBOOK.md`,
  `docs/DATA_SOURCES.md` and `docs/COMPLIANCE.md`.

## In progress

None. M0 stops here per the addendum.

## Blocked

1. **Exact original Section 24.** The pasted addendum references 36 checkboxes but does not
   include their text, and the repository/history does not contain them. The audit uses a
   labelled 36-item reconstruction. Exact sign-off is blocked until the real list is supplied.
2. **Provider credentials.** Live Razorpay, SMTP, SMS, Google Maps, S3 and OCR cannot be
   certified without accounts/keys.
3. **Legal review.** Online pharmacy, Schedule H/H1/X, prescription retention, DPDP, GST and
   consumer-protection compliance require a qualified professional. No launch claim is allowed.
4. **Original Section 10.** The exact order-state specification was not supplied; the audit
   uses `docs/order-lifecycle.md` and the implemented `OrderStateMachine`.

## Next

After the user says **go**:

1. Start M1 with the P0 fixes:
   - pharmacy invoice tenancy;
   - refund transition bypass;
   - customer-visible handover OTP;
   - audit-log unfiltered query;
   - concurrency loser returning `409`;
   - regression tests for all five.
2. Then M2: pharmacist role, verified registration, admin bootstrap, abuse controls.

## Current tree state

M0 documentation only. No Java, TypeScript, SQL, Docker or nginx application change was made
during the audit.

## Audit DB notes

The local demo database was used for live tests. The following records are audit fixtures:

- Order 15: race loser/winner cleanup; cancelled, stock restored.
- Order 16: full COD delivery, then deliberately moved to `REFUNDED` to prove the bypass.
- Orders 17 and 18: idempotency and sandbox-payment probes; cancelled.
- Prescription 1: uploaded/approved test image; review history fixture.
- Customer B (`audit.*@example.com`) and its address: test identity.

These are local development rows, not production data.
