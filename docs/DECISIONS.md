# Decisions

## D-001 — M0 is audit-only

**Decision:** M0 changes documentation only. No application code, migration, dependency,
Docker or frontend behavior is changed.

**Reason:** Addendum v5 Part 2 explicitly says “CHANGE NOTHING” and requires approval before
M1. Changing code during M0 would destroy the audit baseline.

## D-002 — Use a labelled 36-item reconstruction for Section 24

**Decision:** The audit uses a 36-item reconstruction derived from the addendum and current
surface, and marks it clearly as reconstructed.

**Reason:** The Addendum v5 still references the original 36 Section 24 checkboxes, but the pasted text
does not contain them and they are absent from the repository and Git history. Inventing a
verbatim claim would violate the evidence rule. The reconstruction still exposes the real gaps
and gives M1 a testable gate. Exact final sign-off remains blocked until the original text is
provided.

## D-003 — Use a fresh API process for live evidence

**Decision:** Live tests run against a clean `:8081` process built from commit `0e9b76c`.

**Reason:** Rebuilding `target/medidrop-api-1.0.0.jar` while the previous `:8080` JVM was
running caused `NoClassDefFoundError` and misleading timeouts. Those were audit-environment
artifacts, not product bugs. The clean process produced valid results. The runbook now records
the “stop before rebuild” rule.

## D-004 — OSV batch is the actionable Maven scanner for M0

**Decision:** Use OSV’s batch API against `mvn dependency:list` output, with Gitleaks in Docker
for secrets.

**Reason:** `osv-scanner`, `gitleaks` and OWASP Dependency-Check binaries were not installed
locally. Gitleaks ran from Docker. OSV gave a reproducible package/version result without a
large NVD bootstrap. OWASP Dependency-Check remains a later CI hardening item; it was not
claimed as run.

## D-005 — Keep the local demo database as the audit fixture

**Decision:** Use the existing seeded local database and create temporary audit rows instead
of standing up a second dataset.

**Reason:** The goal is to test the real code path with real PostgreSQL semantics. Audit rows
are documented in `PROGRESS.md`. No production data was touched.

## D-006 — Do not “fix” P0 findings in M0

**Decision:** Record cross-tenant invoice access, direct refund transitions, missing pharmacist
role/controlled-medicine rules, the OTP projection gap and the missing pilot gate; do not patch
them in this milestone.

**Reason:** The addendum requires the audit to stop for approval. Fixing P0s before the audit is
accepted would mix discovery and remediation and make the gate unverifiable.

## D-007 — Preserve the modular monolith

**Decision:** Do not propose a microservice split as part of the remediation.

**Reason:** Order placement, stock, coupon redemption and payment state are intentionally one
ACID transaction. The current seams are package/service boundaries. Splitting now would add
distributed-consistency risk without a demonstrated scaling need.

## D-008 — Treat the current UI as functional but not yet launch-grade

**Decision:** Do not spend M0 on a visual redesign. Keep the existing brand foundation and
route the futuristic layer to M14.

**Reason:** P0 correctness and legal gaps dominate. A design pass before fixing authorization
and pharmacist identity would polish the wrong layer.

## D-009 — No AI attribution

**Decision:** Any commit produced from this work uses the user's Git identity and contains no
`Co-Authored-By`, “Generated with” or other AI attribution.

**Reason:** Explicit user instruction.

## D-010 — Use the Addendum v5 milestone order

**Decision:** Keep the v5 order M1–M15, with the current-flow P0 fixes in M1 and the pilot gate
(DEMO_MODE, feature flags, ComplianceSignoff) in M2.

**Reason:** The invoice IDOR, refund bypass and OTP projection are direct security/financial/clinical
failures. The v5 engines (Node Selection, ETA, Rx Vault, Savings, cold chain) depend on later
catalogue, inventory, prescription and tracking work. Starting them before the P0 fixes would put
polish ahead of safety. M1 closes the current-flow P0s; M2 closes the pilot gate before Stage 0/1.

## D-011 — Audit v5 engines as MISSING, not as backlog fiction

**Decision:** Record the v5 Node Selection, ETA, Rx Vault, Savings, cold chain, dispatch and step-event
engines as MISSING; do not describe the existing pharmacy radius, Haversine route or order history as
partial versions of those engines.

**Reason:** A radius filter, a point ETA and status history are useful foundations, but they do not
provide eligibility ranking, ETA ranges/calibration, verified refills, substitution safety or funnel
timestamps. Labelling them as done would create fake capability.

## D-012 — PERF and A11Y are registers, not claims

**Decision:** Add `docs/PERF.md` and `docs/A11Y.md` with target/status/evidence tables; every
unmeasured target is marked NOT VERIFIED or BLOCKED.

**Reason:** Addendum v5 Part 8 requires 250K-seed p95 numbers, 50-way inventory proof, axe zero
serious/critical and Lighthouse ≥90. None can be proven in the current 15-product, H2, no-axe
environment. A register that says so is more useful than an invented pass.

## D-014 — Use pre-pilot SemVer and milestone checkpoints

**Decision:** Use `0.x` versions before pilot, `1.0.0-rc.N` only after the Part 8 and original 36-item
gates are verified, and tag a milestone only after its gate passes. The scheme is in
`docs/VERSIONS.md`; `CHANGELOG.md` records gates, not just code.

**Reason:** “Addendum v5” is a specification, not a product version. Checkpoints make regressions
bisectable and stop a moving `main` from being mistaken for a pilot candidate.

## D-015 — Use a verified pharmacist profile before a separate role

**Decision:** M1 enforces a verified pharmacist profile on the pharmacy (name, registration number,
state council, admin verification) before any prescription review. A separate `PHARMACIST` login role
is deferred to M2.

**Reason:** This closes the immediate unsafe-approval path without fabricating a half-built five-role
auth flow. The M2 change can link the profile to a dedicated account and keep the same authority checks.

## D-016 — Keep the raw handover OTP only until verification

**Decision:** Store the current 4-digit handover code on the order for the customer’s own detail view,
alongside the existing hash, and clear it immediately after successful handover.

**Reason:** The audit proved the UI expected `deliveryOtp` but the API always returned `null`; a hash
cannot be shown. The raw code is short-lived, returned only to the owning customer, and removed at
verification. A future vault/KMS design can replace this without changing the API.

## D-013 — Do not advertise unimplemented v5 flags

**Decision:** Do not add `DEMO_MODE`, `EXPRESS_ENABLED`, `RX_ENABLED`, `SUBSTITUTION_ENABLED`,
`COLD_CHAIN_ENABLED` or `VAULT_ENABLED` to `.env.example` until the backend reads and enforces them.

**Reason:** An environment variable that the app ignores looks like a safety control but is not one.
The pilot gate is a code change in M2, not a documentation change in M0.
