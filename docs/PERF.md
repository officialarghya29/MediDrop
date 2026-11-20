# Performance register

Last updated: 2026-11-20

Status: **PARTIAL. Catalogue search is measured at 250K across two nodes; single- and two-instance search/order load and Lighthouse medians are measured locally and in CI. Nearby-node, node-selection, N+1 and ETA targets remain unverified.**

This file records the target, the current evidence and the blocker. A target is not a pass
until it is measured on the stated dataset and profile.

## 1. Targets from Addendum v5 Part 8

| Metric | Target | Current status | Evidence / blocker |
| --- | --- | --- | --- |
| Search p95 | < 300 ms | **PASS (0.1.8)** | 250,000 synthetic medicines + 250,000 inventory rows; 100 HTTP searches across two API nodes: p50 **189.3 ms**, p95 **286.5 ms**, p99 **293.4 ms**, 0 errors. Slower node p95 **290.6 ms**; 50 samples each. |
| Nearby-node query p95 | < 200 ms | **NOT MEASURED** | No `FulfilmentNode` query, no PostGIS and no nearby-node benchmark. |
| Order placement p95 | < 800 ms | **PASS (0.1.8)** | Two-instance 60-second soak: 31 real COD orders, each added on one node and submitted on the next, p50 **63.7 ms**, p95 **93.9 ms**, p99 **701.5 ms**, 0 errors. Single-node run: p95 **28.7 ms**. |
| Node selection p95 | < 400 ms | **NOT IMPLEMENTED** | No Node Selection Engine exists. |
| Lighthouse performance | >= 90 on mid-range mobile | **PASS (0.1.8 local)** | Lighthouse 12.8.2 mobile, five-run local median: home **93**, search **92**. The manual runner enforces 90/100/100/90. Hosted CI runs five samples and enforces a documented **75** performance floor; it measured **78 / 77**. |
| N+1 query proof | No N+1 on critical paths | **NOT PROVEN** | No query-count assertions or Hibernate statistics gate. |
| ETA error | Report MAE and p90 | **NOT MEASURED** | No ETA prediction records, actual-vs-predicted log or backtest dataset. |
| 50 parallel checkouts on stock 10 | Exactly 10 successes | **PASS** | 0.1.6 live gate: 10 × `200`, 40 × `409`, stock 0, 10 ledger units, 0 errors. |
| Browser + axe journeys | No WCAG A/AA violations | **PASS (0.1.8)** | Playwright 1.65.1 + axe 4.14.0 on Chrome at 390 px: 25 public, customer, console and role journeys across every implemented surface; **25/25 passed**. |

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
- Environment: Linux x86_64, Python 3.12.3, PostgreSQL 16 in Docker, Spring Boot 3.5.16, two API
  instances for the current run, warm-up 10 then **100** measured requests.
- Terms: ten generic names, each returning thousands of rows; every response had to be non-empty.
- Single-node result: **p50 189.1 ms · p95 280.5 ms · p99 286.8 ms · max 288.5 ms · 0 errors**.
- Two-node result: two API processes on the same PostgreSQL database, 50 samples each through the
  updated harness: **p50 189.3 ms · p95 286.5 ms · p99 293.4 ms · max 474.3 ms · 0 errors**. Node
  p95 values were **290.6 ms** and **242.6 ms**.
- The first run measured p95 298.5 ms. `EXPLAIN` showed the lowercased predicate was scanning a
  raw-name B-tree. `V7__catalogue_search_expressions.sql` replaced the unused raw trigram indexes
  with lowercased expression indexes and a prefix index; the single-node rerun measured 280.5 ms.
- CI does not run the 250K gate; it runs the demo-catalogue two-instance soak.

### 2B. Sustained search and order soak (2026-11-20)

- Harness: `infrastructure/scripts/soak-check.py`; now part of the required CI smoke job.
- Dataset: the 18-medicine demo seed on fresh PostgreSQL 16, not the 250K catalogue.
- Single-instance run: three search workers for 60 seconds; one order worker placing a COD order
  every 2 seconds. Search **5,001 requests · p50 5.3 ms · p95 7.9 ms · p99 9.1 ms · 0 errors**;
  orders **31 · p50 23.5 ms · p95 28.7 ms · p99 33.7 ms · 0 errors**.
- Two-instance run: two API processes on one PostgreSQL database; three search workers distributed
  across both; each COD order added on one node and submitted on the next. Local search **4,351
  requests**, split **2,176 / 2,175**, with both nodes at p95 **16.2 ms** (p99 22.9, max 69.4) and 0
  errors; orders **31 · p50 61.6 ms · p95 99.7 ms · p99 680.7 ms · 0 errors**.
- CI run `44780590024`: **4,883 searches at p95 13.1 ms** and **31 orders at p95 61.8 ms**, 0 errors,
  across the same two-instance topology; per-node search p95 was **12.5 / 13.8 ms**.
- Targets: search p95 < 300 ms, order p95 < 800 ms. Both configurations pass.
- Limits: this proves horizontal HTTP routing and shared-database cart/token state on the demo
  catalogue. It is not a 250K mixed search/order workload, a PostGIS workload or a production load
  balancer.

### 2C. Lighthouse mobile (2026-11-20)

- Harness: `infrastructure/scripts/lighthouse-check.py`; pinned Lighthouse **12.8.2**, five runs per
  URL, mobile form factor, built app served by `vite preview` with `/api` proxied to the real API.
- Local home: performance **93**, accessibility **100**, best-practices **100**, SEO **92**; median
  LCP **2,829.8 ms**, TBT **95.5 ms**, CLS **0**.
- Local search: performance **92**, accessibility **100**, best-practices **100**, SEO **92**; median
  LCP **2,849.2 ms**, TBT **101.5 ms**, CLS **0.012**.
- Hosted CI home: **78** performance / 100 accessibility / 100 best-practices / 92 SEO. Search:
  **77 / 100 / 100 / 92**. Samples ranged from 53 to 85, so CI enforces **75** performance; local and
  product threshold remains **90/100/100/90**.
- The Lighthouse job runs five samples per URL in CI. Evidence: `docs/assets/lighthouse-v018.json`
  (local five-run report) and the `lighthouse-evidence` CI artifact.

## 3. Environment limits

- PostgreSQL 16 runs without PostGIS. `V3__search_indexes.sql` enables `pg_trgm`, not PostGIS.
- No `FulfilmentNodes`, `EtaPredictions`, `OrderStepEvents` or node-selection tables exist.
- The 60-second Python soak runs against two API processes in CI; it is not a k6/Gatling/JMeter
  scenario and does not run at 250K.
- The backend test profile uses in-memory **H2**, not Testcontainers with PostgreSQL/PostGIS.
- CI has backend, frontend-with-Playwright, Lighthouse and two-instance smoke-plus-soak jobs; it
  has no 250K or query-count job.

## 4. Measurement plan for later milestones

1. Load 250K catalogue rows with realistic normalization and search indexes in a dedicated
   PostgreSQL/PostGIS environment.
2. Move the soak harness to k6 (or equivalent) for nearby-node lookup, node selection and 250K
   multi-instance contention, reporting p50/p95/p99 and error rate.
3. Keep the **50 checkouts / stock 10** gate green; require exactly 10 successes and 40 conflicts.
4. Add query-count assertions for order, catalogue and node-selection paths.
5. Instrument every order step from the v5 time budget and log predicted vs actual ETA.
6. Move Lighthouse to dedicated mid-range mobile hardware so hosted CI can enforce the 90 product
   target instead of the current 75 floor, and extend it to authenticated critical screens.
7. Record the exact machine, dataset version, concurrency and run date beside every number.

## 5. Gate result

**PARTIAL.** Catalogue search at 250K across two nodes, single- and two-instance search/order load,
Lighthouse and the 50-way stock burst are measured. Nearby-node, node-selection, N+1, ETA,
production-network load and full accessibility remain unverified. M15 cannot pass until the remaining
targets are measured on the stated dataset and profile.
