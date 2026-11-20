# Versions and checkpoints

MediDrop uses two related tracks: **milestone tags** for engineering progress and **SemVer
pre-releases** for anything that can be run as a candidate. The project is pre-pilot, so it must not
claim `1.0.0` yet.

## Scheme

| Stage | Version line | Meaning |
| --- | --- | --- |
| Audit / foundation | `0.1.x` | M0 audit and M1 safety fixes. No pilot claim. |
| Core flows | `0.2.x`–`0.9.x` | One minor per completed milestone group; breaking API/schema changes still bump minor. |
| Pilot candidate | `1.0.0-rc.N` | Part 8 and the original 36-item gate are verified; legal/ops sign-off still required. |
| Pilot | `1.0.x` | Only patch fixes during a closed pilot. New capability waits for `1.1.0`. |

### Rules

- A milestone tag is created **only after** its gate passes and the commit is pushed.
- Tags use annotated Git tags authored by the project identity. GPG signing is used when a signing
  key is configured.
- The changelog records the gate, not just the code changed.
- A minor version never means “feature complete”; it means the milestone gate is green.
- `docs/AUDIT.md` and `docs/PROGRESS.md` remain the source of truth. A tag without a green gate is
  deleted, not relabelled.

## Current checkpoints

| Version | Scope | State |
| --- | --- | --- |
| [`v0.1.0-m0`](https://github.com/officialarghya29/MediDrop/releases/tag/v0.1.0-m0) | Repository audit only; no application code | **Released pre-release** |
| [`v0.1.1`](https://github.com/officialarghya29/MediDrop/releases/tag/v0.1.1) | M1 safety slice and deep-scan hardening; pre-pilot | **Released pre-release** |
| [`v0.1.2`](https://github.com/officialarghya29/MediDrop/releases/tag/v0.1.2) | Lower-cost substitute comparison with safety filters | **Released pre-release** |
| [`v0.1.3`](https://github.com/officialarghya29/MediDrop/releases/tag/v0.1.3) | Substitute fallback reliability | Published foundation release |
| [`v0.1.6`](https://github.com/officialarghya29/MediDrop/releases/tag/v0.1.6) | Realtime suspension enforcement, inactive-pharmacy RX guard, two-channel verification, production runtime controls | **Current foundation release** |
| [`v0.1.5`](https://github.com/officialarghya29/MediDrop/releases/tag/v0.1.5) | Suspended-provider authority, prescription privacy state, measured 50-way stock gate | Previous release |
| [`v0.1.4`](https://github.com/officialarghya29/MediDrop/releases/tag/v0.1.4) | OTP identity guard, versioned order transitions, rider reassignment | Previous release |
| `v0.2.0-m1` | Remaining M1 gate: one-command run, design tokens (50-way stock closed) | In progress |
| `v0.3.0-m2` | Five roles, pilot gates, DEMO_MODE and ComplianceSignoff | Not started |

`v0.1.1` and `v0.1.2` remain historical pre-releases. `v0.1.6` is the current downloadable
foundation build and the latest normal release; this is still not a pilot-readiness claim.

## Why this helps

- Each milestone has a reproducible checkout instead of a moving `main`.
- A regression can be bisected to a checkpoint.
- The version number tells a reviewer whether they are looking at an audit, a safety fix or a
  pilot candidate.
- It stops “v5” from being confused with a release: `v5` is the addendum, `0.x` is the product.
