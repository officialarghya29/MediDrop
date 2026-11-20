# Changelog

All notable changes are recorded here. Versions follow `docs/VERSIONS.md`; a version is only tagged
after its gate passes.

## [0.2.0-m1] — in progress

### Fixed

- Pharmacy invoice reads are scoped to the authenticated pharmacy.
- Refund terminal states cannot be reached through the admin status endpoint; refunds flow through
  `REFUND_PENDING` and a gateway/ledger record.
- Customer order detail returns the current handover OTP until it is verified; rider copy now says
  four digits.
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
- Pharmacy documents now require a matching PDF/JPEG/PNG signature.
- Delivery failure is audited as a delivery action; completion requires `OUT_FOR_DELIVERY`.
- Console SMS/email no longer log OTP or reset bodies; admin setting changes are audited.
- Refund path/body order IDs must match; customer OTP is hidden once delivery is closed.

### Added

- `docs/VERSIONS.md` and this changelog.
- Pharmacist verification endpoint and audit action.
- Regression tests for refund bypass, invoice tenancy, audit listing, prescription authority, OTP enumeration, review-history tenancy and pharmacy-registration response leakage.

## [0.1.0-m0] — 2026-11-20

### Added

- Addendum v5 audit against the 36-item reconstruction and Part 8.
- `docs/AUDIT.md`, `PROGRESS`, `DECISIONS`, `RUNBOOK`, `DATA_SOURCES`, `COMPLIANCE`, `PERF`, `A11Y`.
