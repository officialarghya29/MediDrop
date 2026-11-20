# Changelog

All notable changes are recorded here. Versions follow `docs/VERSIONS.md`; a version is only tagged
after its gate passes.

## [0.1.7] — 2026-11-20

**Measured-edge patch. Pre-pilot; not `1.0.0`.**

### Fixed

- Catalogue search now uses lowercased trigram expression indexes. The previous indexes were on the
  raw columns while every predicate used `lower(...)`, so PostgreSQL could not use them. V7 replaces
  them with matching expression indexes and adds a prefix index for typeahead suggestions.
- The logo accessible name no longer disagrees with its visible wordmark and tagline. The fix was
  driven by the new axe gate; no accessibility rule was suppressed.

### Added

- `catalogue-load-check.py`: a repeatable 250,000-row, 100-request HTTP search gate with p50/p95/p99,
  error and empty-result checks.
- Playwright + axe browser gate for the public home, search, medicine detail and sign-in pages, plus
  a search-to-detail journey. It runs in the required frontend CI job at 390 px.

### Changed

- Updated React Query **5.107.2**, Zustand **4.5.7**, TypeScript **5.9.3**, Testing Library React
  **16.3.3**, Autoprefixer **10.6.2** and Springdoc **2.10.0**.

### Verification

- Backend: **52 passed, 0 failed**.
- Frontend: **13 passed, 0 failed**; production build passed.
- Browser + axe: **5 passed, 0 failed**.
- 250K search: **p50 189.1 ms · p95 280.5 ms · p99 286.8 ms · 0 errors** against PostgreSQL 16.
- Live API smoke: **33 passed, 0 failed**.
- `npm audit --omit=dev --audit-level=high`: **0 vulnerabilities**.
- Fresh PostgreSQL 16 migration: **V1–V7**.

## [0.1.6] — 2026-11-20

**Production-hardening patch. Pre-pilot; not `1.0.0`.**

### Fixed

- Suspended pharmacies and riders now lose realtime tracking/delivery subscriptions. The STOMP
  interceptor re-checks provider approval and active state before authorising a channel.
- Prescription upload only accepts an approved **and active** pharmacy, closing the
  approved-but-inactive assignment path.
- Checkout now requires both email and mobile verification; either-channel verification was not
  enough for a healthcare transaction.
- Production runtime now drains in-flight requests for 30 seconds on shutdown, exposes
  liveness/readiness probes, enables Hikari leak detection and batches JDBC writes.

### Repository

- Added a CI gate for production npm vulnerabilities.
- Enabled `main` branch protection: no force-push/deletion, required CI contexts and conversation
  resolution for pull requests. Admin direct pushes remain available for this release rail.
- Dependabot now ignores major-version jumps and only auto-opens minor/patch updates. Eight unsafe
  major PRs were closed with migration rationale.

### Verification

- Backend: **52 passed, 0 failed**.
- Frontend: **13 passed, 0 failed**; production build passed.
- Live PostgreSQL 16: **33 smoke checks passed, 0 failed**; health, liveness and readiness `UP`.
- 50-way stock gate: **10 winners, 40 conflicts, 10 orders, stock 0, 10 SALE units, 0 errors**.
- `npm audit --omit=dev --audit-level=high`: **0 vulnerabilities**.

## [0.1.5] — 2026-11-20

**Foundation hardening. Pre-pilot; not `1.0.0`.**

### Fixed

- Suspended delivery partners can no longer accept, advance or complete an existing assignment.
  Every delivery write now requires an `APPROVED` partner, not just a valid session.
- Suspended pharmacies can no longer accept, prepare or hand over medicine. They may still close an
  existing order through cancellation, but cannot resume fulfilment.
- Prescription review, queue access and file reads now require an approved, active pharmacy. A
  suspended pharmacy immediately loses access to prescription data.
- Inventory and catalogue maintenance require an active pharmacy, closing the suspension bypass.

### Verification

- Backend: **45 passed, 0 failed**.
- Frontend: **13 passed, 0 failed**.
- Production frontend build: **passed**.
- Measured 50-way stock race on fresh PostgreSQL 16: **10 winners, 40 conflicts, 10 orders,
  stock 0, 10 units sold, 0 server errors**.
- Fresh PostgreSQL 16 migration: **V1–V6**.
- Live API smoke: **33 passed, 0 failed**.
- `npm audit --omit=dev`: **0 vulnerabilities**.

### Repository

- Added weekly Dependabot updates for Maven, npm and GitHub Actions.
- Added `SECURITY.md` with a private disclosure route and explicit scope.
- Added `infrastructure/scripts/stock-race-check.py` so the 50-way gate is repeatable.

## [0.1.4] — 2026-11-20

**Foundation hardening. Pre-pilot; not `1.0.0`.**

### Fixed

- OTP verification now refuses locked, disabled and deletion-pending accounts before issuing tokens.
- OTP and password-reset rows are read with pessimistic write locks, so concurrent requests cannot
  consume the same single-use token twice.
- Orders now carry an optimistic version (`V6`). A stale status transition receives a recoverable
  conflict instead of overwriting a newer cancellation, acceptance or refund.
- A rider rejection replaces the rider while the order remains `DELIVERY_ASSIGNED`; the order no
  longer strands because reassignment expected `READY_FOR_PICKUP`.
- The console version label now follows the release instead of showing a stale `v1.0`.

### Presentation

- Rebuilt the README as a verified command deck with a new futuristic hero, live telemetry, model
  invariants, surface gallery, release rail and explicit launch boundaries.
- Regenerated quality/architecture assets against the current test and schema counts.

### Verification

- Backend: **43 passed, 0 failed**.
- Frontend: **13 passed, 0 failed**.
- Production frontend build: **passed**.
- Fresh PostgreSQL 16 migration: **V1–V6**.
- Live API smoke: **33 passed, 0 failed**.
- `npm audit --omit=dev`: **0 vulnerabilities**.

## [0.1.3] — 2026-11-20

**Foundation release. Pre-pilot; not `1.0.0`.**

### Fixed

- When no pharmacy is supplied to the substitute endpoint, the service now selects the cheapest
  pharmacy that is approved, active and accepting orders, instead of stopping at a paused cheapest
  pharmacy and returning no alternatives.
- Added a regression for this fallback.

### Verification

- Backend: **40 passed, 0 failed**.
- Frontend: **13 passed, 0 failed**.
- Production frontend build: **passed**.
- Live PostgreSQL fallback: paused Salt Lake pharmacy; Bhowanipore selected with a ₹255 vs ₹343
  candidate (₹88, 26%). Demo prices only.

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

The M1 code is present and tested in `0.1.6`; the milestone tag remains withheld until the
one-command developer entry point and design-token pass close. The 50-way stock gate is measured.

## [0.1.0-m0] — 2026-11-20

### Added

- Addendum v5 audit against the 36-item reconstruction and Part 8.
- `docs/AUDIT.md`, `PROGRESS`, `DECISIONS`, `RUNBOOK`, `DATA_SOURCES`, `COMPLIANCE`, `PERF`, `A11Y`.
