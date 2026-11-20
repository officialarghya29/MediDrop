# Performance register

Last updated: 2026-11-20

Status: **PARTIAL. Catalogue search is measured at 250K; sustained search/order load and Lighthouse are measured. Nearby-node, node-selection, N+1 and ETA targets remain unverified.**

This file records the target, the current evidence and the blocker. A target is not a pass
until it is measured on the stated dataset and profile.

## 1. Targets from Addendum v5 Part 8

| Metric | Target | Current status | Evidence / blocker |
| --- | --- | --- | --- |
| Search p95 | < 300 ms | **PASS (0.1.7)** | 250,000 synthetic medicines + 250,000 inventory rows; 100 HTTP searches through the API: p50 **189.1 ms**, p95 **280.5 ms**, p99 **286.8 ms**, 0 errors. Local single node, PostgreSQL 16. |
| Nearby-node query p95 | < 200 ms | **NOT MEASURED** | No `FulfilmentNode` query, no PostGIS and no nearby-node benchmark. |
| Order placement p95 | < 800 ms | **PASS (0.1.8)** | 60-second soak: 31 real COD orders through search → cart → `POST /orders`, p50 **23.5 ms**, p95 **28.7 ms**, p99 **33.7 ms**, 0 errors. Local demo catalogue. |
| Node selection p95 | < 400 ms | **NOT IMPLEMENTED** | No Node Selection Engine exists. |
| Lighthouse performance | >= 90 on mid-range mobile | **PASS (0.1.8)** | Lighthouse 12.8.2 mobile, three-run median on the built app: home **92**, search **93**. One home sample scored 82, so the median is not a guaranteed floor. |
| N+1 query proof | No N+1 on critical paths | **NOT PROVEN** | No query-count assertions or Hibernate statistics gate. |
| ETA error | Report MAE and p90 | **NOT MEASURED** | No ETA prediction records, actual-vs-predicted log or backtest dataset. |
| 50 parallel checkouts on stock 10 | Exactly 10 successes | **PASS** | 0.1.6 live gate: 10 × `200`, 40 × `409`, stock 0, 10 ledger units, 0 errors. |
| Browser + axe journeys | No WCAG A/AA violations | **PASS (0.1.8)** | Playwright 1.65.1 + axe 4.14.0 on Chrome at 390 px: five public and four authenticated journeys (checkout, pharmacy, delivery, admin); **9/9 passed**. |

## 2. What was actually measured in M0

These are build/test facts, not performance results:

- Backend: **52 tests passed** at the 0.1.6 baseline.
- Frontend: **13 tests passed**; production build passed.
- OpenAPI document: **200**, 96,425 bytes, approximately **1.5 s** on the clean local API. This is a
  single documentation-endpoint observation, not a p95.
- `npm audit`: **0** vulnerabilities.
- Maven OSV batch: **2** Spring WebMVC advisories.
- Gitleaks: 11 commits, **no leaks**.

### 2A. 250K search measurement (2026-11-20)

- Harness: `infrastructure/scripts/catalogue-load-check.py`.
- Dataset: **250,000** benchmark medicines plus one inventory row per medicine, alongside the normal
  demo seed. Rows are marked `load://catalogue` and the seed is idempotent.
- Environment: Linux x86_64, Python 3.12.3, PostgreSQL 16 in Docker, Spring Boot 3.5.16, single API
  instance, warm-up 10 then **100** measured requests.
- Terms: ten generic names, each returning thousands of rows; every response had to be non-empty.
- Result: **p50 189.1 ms · p95 280.5 ms · p99 286.8 ms · max 288.5 ms · 0 errors**.
- The first run measured p95 298.5 ms. `EXPLAIN` showed the lowercased predicate was scanning a
  raw-name B-tree. `V7__catalogue_search_expressions.sql` replaced the unused raw trigram indexes
  with lowercased expression indexes and a prefix index; the second run measured 280.5 ms.
- This is one local run, not a soak or a multi-instance result. CI does not run the 250K gate.

### 2B. Sustained search and order soak (2026-11-20)

- Harness: `infrastructure/scripts/soak-check.py`; now part of the required CI smoke job.
- Dataset: the 18-medicine demo seed on fresh PostgreSQL 16, not the 250K catalogue.
- Load: three search workers for 60 seconds; one order worker placing a COD order every 2 seconds.
- Search: **5,001 requests · p50 5.3 ms · p95 7.9 ms · p99 9.1 ms · max 36.7 ms · 0 errors**.
- Orders: **31 orders · p50 23.5 ms · p95 28.7 ms · p99 33.7 ms · max 33.7 ms · 0 errors**.
- Targets: search p95 < 300 ms, order p95 < 800 ms. Both pass.
- This is a single local API and database. It proves sustained behaviour on one node, not horizontal
  scaling or multi-instance contention.

### 2C. Lighthouse mobile (2026-11-20)

- Harness: `infrastructure/scripts/lighthouse-check.py`; pinned Lighthouse **12.8.2**, three runs per
  URL, mobile form factor, built app served by `vite preview` with `/api` proxied to the real API.
- Home: performance **92**, accessibility **100**, best-practices **100**, SEO **92**; median LCP
  **2,930.6 ms**, TBT **61.5 ms**, CLS **0**.
- Search: performance **93**, accessibility **100**, best-practices **100**, SEO **92**; median LCP
  **2,781.6 ms**, TBT **51.5 ms**, CLS **0.012**.
- Variance was material: one home sample scored 82 with a 7.9 s speed index. The result is the median,
  not a floor. Lighthouse is recorded manually rather than enforced in CI until the variance is
  reduced or the runner is pinned to dedicated hardware.
- Evidence: `docs/assets/lighthouse-v018.json`.

## 3. Environment limits

- PostgreSQL 16 runs without PostGIS. `V3__search_indexes.sql` enables `pg_trgm`, not PostGIS.
- No `FulfilmentNodes`, `EtaPredictions`, `OrderStepEvents` or node-selection tables exist.
- A 60-second Python soak harness now exists; there is no k6/Gatling/JMeter multi-instance scenario.
- The backend test profile uses in-memory **H2**, not Testcontainers with PostgreSQL/PostGIS.
- CI has backend, frontend-with-Playwright and smoke-plus-soak jobs; it has no Lighthouse, 250K or
  query-count job.

## 4. Measurement plan for later milestones

1. Load 250K catalogue rows with realistic normalization and search indexes in a dedicated
   PostgreSQL/PostGIS environment.
2. Move the soak harness to k6 (or equivalent) for nearby-node lookup, node selection and
   multi-instance contention, reporting p50/p95/p99 and error rate.
3. Keep the **50 checkouts / stock 10** gate green; require exactly 10 successes and 40 conflicts.
4. Add query-count assertions for order, catalogue and node-selection paths.
5. Instrument every order step from the v5 time budget and log predicted vs actual ETA.
6. Stabilise Lighthouse on dedicated mid-range mobile hardware, then add a CI threshold for the
   public journey and the authenticated critical screens.
7. Record the exact machine, dataset version, concurrency and run date beside every number.

## 5. Gate result

**PARTIAL.** Catalogue search at 250K, sustained search/order load, Lighthouse and the 50-way stock
burst are measured. Nearby-node, node-selection, N+1, ETA, multi-instance load and full accessibility
remain unverified. M15 cannot pass until the remaining targets are measured on the stated dataset and
profile.
