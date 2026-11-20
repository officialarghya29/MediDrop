# Changelog

All notable changes are recorded here. Versions follow `docs/VERSIONS.md`; a version is only tagged
after its gate passes.

## [0.1.2] — 2026-11-20

**Pre-release feature addition. Not pilot-ready and not `1.0.0`.**

### Added

- **Lower-cost substitute comparison** on medicine detail pages. The API returns only products with the
  same generic composition, strength, dosage form, pack size, prescription flag and schedule class.
- Candidates must be in stock at the selected pharmacy and strictly cheaper than the original.
- The response includes price, savings amount, savings percentage and a pharmacist-review flag.
- Prescription substitutes are never automatic; the UI links to the alternative and keeps the current
  cart/prescription unchanged.
- Three demo-only equivalents demonstrate the feature on a fresh seed.

### Verification

- Backend: **39 passed, 0 failed**.
- Frontend: **13 passed, 0 failed**.
- Production frontend build: **passed**.
- Live PostgreSQL seed check: ₹343 reference returned a ₹249 same-composition value pack, saving ₹94
  (27%) at the same approved pharmacy. Demo prices only.

## [0.1.1] — 2026-11-20

**Pre-release foundation hardening. Not pilot-ready and not `1.0.0`.**

### Fixed

- Pharmacy invoice reads are scoped to the authenticated pharmacy.
- Refund terminal states cannot be reached through the admin status endpoint; refunds flow through
  `REFUND_PENDING` and a gateway/ledger record.
- Customer order detail returns the current handover OTP only during an active delivery.
- Audit-log listing no longer sends a nullable action parameter to PostgreSQL.
- Inventory checkout uses a projection before locking, removing the stale-version 500; optimistic
  conflicts map to `409`.
- Verified pharmacist identity is required before prescription review.
- Medicine schedule `H/H1/X/UNKNOWN` forces prescription review, and `sales_blocked` is enforced.
- Database checks reject price above MRP and invalid stock balances.
- Public pharmacy/delivery onboarding is explicitly permitted instead of returning `401`.
- Pharmacy registration no longer echoes the plaintext password, and duplicate phone numbers are
  rejected by pharmacy and delivery registration.
- Prescription review history is scoped to the calling pharmacy.
- OTP request/verify no longer disclose whether an email has an account.
- Pharmacy documents require a matching PDF/JPEG/PNG signature.
- Delivery failure is audited as a delivery action; completion requires `OUT_FOR_DELIVERY`.
- Console SMS/email no longer log OTP or reset bodies; admin setting changes are audited.
- Refund path/body order IDs must match.

### Added

- `docs/VERSIONS.md`, release notes and a visible GitHub pre-release.
- Runtime build metadata through Spring Boot `build-info`.
- Pharmacist verification endpoint and audit action.
- Regression tests for refund bypass, invoice tenancy, audit listing, prescription authority, OTP
  enumeration, review-history tenancy and pharmacy-registration response leakage.

### Verification

- Backend: **37 passed, 0 failed**.
- Frontend: **13 passed, 0 failed**.
- Production frontend build: **passed**.
- CI on the release commit: **backend, frontend and PostgreSQL smoke all successful**.

### Known launch blockers

- M2–M15 capabilities, including the separate pharmacist role, pilot gates, Node Selection, ETA,
  Rx Vault, Savings, cold chain and dispatch engines.
- Live Razorpay, SMTP, SMS, Maps, S3 and OCR certification.
- 50-way inventory, 250K catalogue, axe, Lighthouse and browser-driven journey gates.
- Legal, regulatory and operations sign-off.

## [0.2.0-m1] — unreleased

The M1 code is present and tested in `0.1.1`; the milestone tag remains withheld until the 50-way
stock gate, one-command developer entry point and design-token pass close.

## [0.1.0-m0] — 2026-11-20

### Added

- Addendum v5 audit against the 36-item reconstruction and Part 8.
- `docs/AUDIT.md`, `PROGRESS`, `DECISIONS`, `RUNBOOK`, `DATA_SOURCES`, `COMPLIANCE`, `PERF`, `A11Y`.
