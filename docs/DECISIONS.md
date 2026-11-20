# Decisions

## D-001 — M0 is audit-only

**Decision:** M0 changes documentation only. No application code, migration, dependency,
Docker or frontend behavior is changed.

**Reason:** Addendum v3 Part 2 explicitly says “CHANGE NOTHING” and requires approval before
M1. Changing code during M0 would destroy the audit baseline.

## D-002 — Use a labelled 36-item reconstruction for Section 24

**Decision:** The audit uses a 36-item reconstruction derived from the addendum and current
surface, and marks it clearly as reconstructed.

**Reason:** The addendum references the original 36 Section 24 checkboxes, but the pasted text
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
role/controlled-medicine rules and the OTP projection gap; do not patch them in this milestone.

**Reason:** The addendum requires the audit to stop for approval. Fixing P0s before the audit is
accepted would mix discovery and remediation and make the gate unverifiable.

## D-007 — Preserve the modular monolith

**Decision:** Do not propose a microservice split as part of the remediation.

**Reason:** Order placement, stock, coupon redemption and payment state are intentionally one
ACID transaction. The current seams are package/service boundaries. Splitting now would add
distributed-consistency risk without a demonstrated scaling need.

## D-008 — Treat the current UI as functional but not yet launch-grade

**Decision:** Do not spend M0 on a visual redesign. Keep the existing brand foundation and
route the futuristic layer to M11.

**Reason:** P0 correctness and legal gaps dominate. A design pass before fixing authorization
and pharmacist identity would polish the wrong layer.

## D-009 — No AI attribution

**Decision:** Any commit produced from this work uses the user's Git identity and contains no
`Co-Authored-By`, “Generated with” or other AI attribution.

**Reason:** Explicit user instruction.

## D-010 — Milestone order changes at M1/M2

**Decision:** Pull the invoice IDOR, refund bypass, OTP projection and audit-log/concurrency
defects into M1; put pharmacist identity and abuse controls in M2.

**Reason:** These are not “auth polish”; they are direct cross-tenant/financial/clinical
failures. They must be closed before catalogue or design work. The addendum allows the
milestone order to be adjusted to what is already done and what the audit finds.
