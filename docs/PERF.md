# Performance register

Last updated: 2026-11-20

Status: **NOT VERIFIED. No Addendum v5 performance target has been measured.**

This file records the target, the current evidence and the blocker. A target is not a pass
until it is measured on the stated dataset and profile.

## 1. Targets from Addendum v5 Part 8

| Metric | Target | Current status | Evidence / blocker |
| --- | --- | --- | --- |
| Search p95 | < 300 ms | **NOT MEASURED** | Catalogue is 15 synthetic medicines, not 250K. `MedicineSearchTest` checks correctness, not latency. |
| Nearby-node query p95 | < 200 ms | **NOT MEASURED** | No `FulfilmentNode` query, no PostGIS and no nearby-node benchmark. |
| Order placement p95 | < 800 ms | **NOT MEASURED** | No load harness or percentile capture. The two-customer race produced one `200` and one `500`; it is a correctness finding, not a latency sample. |
| Node selection p95 | < 400 ms | **NOT IMPLEMENTED** | No Node Selection Engine exists. |
| Lighthouse performance | >= 90 on mid-range mobile | **NOT MEASURED** | Lighthouse is not installed or run in CI. |
| N+1 query proof | No N+1 on critical paths | **NOT PROVEN** | No query-count assertions or Hibernate statistics gate. |
| ETA error | Report MAE and p90 | **NOT MEASURED** | No ETA prediction records, actual-vs-predicted log or backtest dataset. |
| 50 parallel checkouts on stock 10 | Exactly 10 successes | **NOT RUN** | Only a two-customer, one-unit race was tested. |

## 2. What was actually measured in M0

These are build/test facts, not performance results:

- Backend: **28 tests passed**.
- Frontend: **13 tests passed**; production build passed.
- OpenAPI document: **200**, 96,425 bytes, approximately **1.5 s** on the clean local API. This is a
  single documentation-endpoint observation, not a p95.
- `npm audit`: **0** vulnerabilities.
- Maven OSV batch: **2** Spring WebMVC advisories.
- Gitleaks: 11 commits, **no leaks**.

No test durations or API latency percentiles were retained as performance evidence.

## 3. Environment limits

- The audit database contains **15 demo medicines**, not the required 250K-product seed.
- PostgreSQL 16 runs without PostGIS. `V3__search_indexes.sql` enables `pg_trgm`, not PostGIS.
- No `FulfilmentNodes`, `EtaPredictions`, `OrderStepEvents` or node-selection tables exist.
- No k6, Gatling, JMeter or equivalent load harness is in the repository.
- The backend test profile uses in-memory **H2**, not Testcontainers with PostgreSQL/PostGIS.
- CI has backend, frontend and smoke jobs; it has no performance, Lighthouse or query-count job.

## 4. Measurement plan for later milestones

1. Load 250K catalogue rows with realistic normalization and search indexes in a dedicated
   PostgreSQL/PostGIS environment.
2. Add k6 (or equivalent) scenarios for search, nearby-node lookup, order placement and node
   selection, reporting p50/p95/p99 and error rate.
3. Seed concurrent stock and run the **50 checkouts / stock 10** gate; require exactly 10
   successes and 40 conflict responses.
4. Add query-count assertions for order, catalogue and node-selection paths.
5. Instrument every order step from the v5 time budget and log predicted vs actual ETA.
6. Add Lighthouse CI with a mid-range mobile profile and publish the report.
7. Record the exact machine, dataset version, concurrency and run date beside every number.

## 5. Gate result

**BLOCKED / NOT VERIFIED.** No performance claim is made. M15 cannot pass until the targets above
are measured on the stated dataset and profile.
