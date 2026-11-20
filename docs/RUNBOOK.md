# Runbook

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22.12+
- PostgreSQL 16
- Docker + Compose (for the full stack)
- `curl`; Python 3 for the audit harness (optional)

## Full stack with Docker

```bash
cp .env.example .env
# Required:
#   MEDIDROP_JWT_SECRET=$(openssl rand -base64 48)
#   POSTGRES_PASSWORD=<strong password>
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8080/actuator/health
```

Surfaces:

- Web: <http://localhost:3000>
- API: <http://localhost:8080>
- Health: <http://localhost:8080/actuator/health>
- Liveness / readiness: `/actuator/health/liveness`, `/actuator/health/readiness`
- OpenAPI (dev only): <http://localhost:8080/swagger-ui.html>

The Compose profile is `prod`; seeding is disabled and OpenAPI is disabled by default.

## Manual development

```bash
createdb medidrop

cd backend
MEDIDROP_JWT_SECRET=dev-only-medidrop-secret-change-before-deploy-9f3c1a \
MEDIDROP_SEED_ENABLED=true \
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/ws` to `:8080`; open <http://localhost:5173>.

### Demo accounts

Only when `MEDIDROP_SEED_ENABLED=true`:

| Role | Email | Password |
| --- | --- | --- |
| Admin | `admin@medidrop.local` | `Admin@12345` |
| Customer | `customer@medidrop.local` | `Demo@12345` |
| Pharmacy | `pharmacy@medidrop.local` | `Demo@12345` |
| Pharmacy 2 | `pharmacy2@medidrop.local` | `Demo@12345` |
| Delivery | `delivery@medidrop.local` | `Demo@12345` |
| Delivery 2 | `delivery2@medidrop.local` | `Demo@12345` |

These credentials are demo-only. Never seed a production database with them.

## Memory files

Read all of these at session start: `docs/AUDIT.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`,
`docs/RUNBOOK.md`, `docs/DATA_SOURCES.md`, `docs/COMPLIANCE.md`, `docs/PERF.md`, `docs/A11Y.md`.

## Test gates

```bash
cd backend
mvn -B -ntp test
```

```bash
cd frontend
npm test
npm run build
npm audit
```

Live smoke (API must be running with seed data):

```bash
infrastructure/scripts/smoke-test.sh
```

Expected current baseline: **52** backend tests, **13** frontend tests, **16** Playwright/axe checks,
**33** smoke checks, a passing two-instance 60-second search/order soak, a passing 50-way stock race
and 250K search p95 below 300 ms. Flyway now runs **V1–V7**; V7 adds lowercased search expression
indexes.

The current gate is **still not** the v5 Part 8 gate. Public/customer/role Playwright/axe, a 250K
search harness, a two-instance soak and a median-threshold Lighthouse report now exist, but
Testcontainers/PostGIS, a Lighthouse CI threshold, a coverage threshold, 250K multi-instance load,
console-subpage axe and full keyboard/screen-reader coverage are missing. The 50-way stock harness
does not replace those gates. Do not claim Part 8 from a green CI run. See `docs/PERF.md` and
`docs/A11Y.md`.

Browser/axe gate:

```bash
cd frontend
npm run build
npm run test:e2e
```

250K search gate (API and disposable PostgreSQL running):

```bash
python3 infrastructure/scripts/catalogue-load-check.py \
  --base-url http://127.0.0.1:8080 \
  --rows 250000 --samples 100 --p95-ms 300 \
  --psql-container medidrop-gate
```

Two-instance search/order soak (two APIs on one seeded database; CI does this after smoke):

```bash
python3 infrastructure/scripts/soak-check.py \
  --base-url http://127.0.0.1:8080 \
  --base-url http://127.0.0.1:8081 \
  --duration-seconds 60 --search-workers 3 --order-interval-seconds 2
```

Lighthouse (built preview with API proxied):

```bash
cd frontend
VITE_PROXY_TARGET=http://127.0.0.1:8080 npm run preview -- --port 4174
# second terminal, from the repository root
python3 infrastructure/scripts/lighthouse-check.py --base-url http://127.0.0.1:4174
```

`DEMO_MODE`, the v5 feature flags and `ComplianceSignoff` are not implemented. Do not set
undocumented environment variables and assume they gate RX, substitution or Express.

## Compose validation

```bash
MEDIDROP_JWT_SECRET=compose-test-secret-with-at-least-32-characters \
POSTGRES_PASSWORD=compose-test-db-password \
docker compose config

cp .env.example /tmp/medidrop.env
# Add the two required values to /tmp/medidrop.env.
docker compose --env-file /tmp/medidrop.env config
```

Both were verified in M0.

## Version checkpoints

Read `docs/VERSIONS.md`. A milestone tag is created only after its gate is green and the commit is
pushed. Do not tag `v0.2.0-m1` until the remaining M1 items (one-command entry point and design
tokens) close; the 50-way stock gate is already measured.

## Rebuilding the API safely

**Stop the running JVM before replacing the jar.** Replacing a jar under a running JVM causes
lazy class-loading failures such as:

```text
NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy
```

Then requests can hang or return misleading 5xx errors. Use:

```bash
# Stop the API process first.
cd backend
mvn -B -ntp -DskipTests package
java -jar target/medidrop-api-*.jar
```

For parallel audit work, run the fresh build on another port:

```bash
java -jar target/medidrop-api-*.jar --server.port=8081
```

## Publishing a release

1. Confirm `CHANGELOG.md` has a dated section and `backend/pom.xml` / `frontend/package.json`
   carry the same version.
2. Run `mvn -B -ntp verify` and `npm test && npm run build`; wait for CI on the exact commit.
3. Create an annotated tag and push it:
   `git tag -a v0.1.7 -m "MediDrop 0.1.7" && git push origin v0.1.7`.
4. Publish a **normal latest release** with attached artifacts:
   `gh release create v0.1.7 --verify-tag --latest --notes-file docs/releases/v0.1.7.md`.
   Use `--prerelease` only when the build is deliberately not the latest runnable foundation.
5. Never label a build `1.0.0` or “launch-ready” until the Part 8/original-36 gate, provider
   certification and legal/ops sign-off are complete.

## Environment variables

| Variable | Required | Purpose |
| --- | --- | --- |
| `MEDIDROP_JWT_SECRET` | yes | HMAC key ≥32 bytes |
| `MEDIDROP_DB_URL` | yes | JDBC URL |
| `MEDIDROP_DB_USERNAME` / `MEDIDROP_DB_PASSWORD` | yes | Database credentials |
| `MEDIDROP_DB_POOL_SIZE` | no | Hikari pool size |
| `MEDIDROP_CORS_ORIGINS` | split origin | Browser origins |
| `MEDIDROP_OPENAPI_ENABLED` | no | Dev/test only; false in prod |
| `MEDIDROP_PUBLIC_URL` / `MEDIDROP_FRONTEND_URL` | yes | Links in notifications |
| `MEDIDROP_STORAGE_PATH` | local storage | Private prescription volume |
| `MEDIDROP_PAYMENTS_PROVIDER` | no | `sandbox` or `razorpay` |
| `RAZORPAY_*` | live payments | Gateway and webhook secrets |
| `MEDIDROP_MAPS_PROVIDER` / `MEDIDROP_MAPS_API_KEY` | no | Haversine or Google |
| `MEDIDROP_MAIL_PROVIDER` / `MEDIDROP_SMS_PROVIDER` | no | Console or real provider |
| `MEDIDROP_SEED_ENABLED` | no | Demo data; false in prod |
| `MEDIDROP_ADMIN_PASSWORD` | dev seed | Seeded admin only |
| `PORT` | no | API port |

## Audit reproduction

The M0 harness lives outside the repository at `/tmp/medidrop-audit/` during the session.
Key checks:

```bash
# idempotency
# sequential same X-Idempotency-Key -> same order id

# cross-tenant invoice
curl -H "Authorization: Bearer <pharmacy2-token>" \
  http://localhost:8081/api/v1/pharmacy/orders/14/invoice

# unfiltered audit logs
curl -H "Authorization: Bearer <admin-token>" \
  http://localhost:8081/api/v1/admin/audit-logs

# filtered audit logs
curl -H "Authorization: Bearer <admin-token>" \
  "http://localhost:8081/api/v1/admin/audit-logs?action=LOGIN_SUCCESS"
```

Do not commit tokens or real `.env` files. The audit used ephemeral local tokens only.

## Operations

- Health: `GET /actuator/health`; Compose healthchecks use it.
- Logs: API stdout. Never log tokens, OTPs or prescription contents.
- Migrations: Flyway on boot; run one instance during migration.
- Backups: **no tested restore exists yet**. M15 must add a backup/restore drill for PostgreSQL and
  the prescription volume before any pilot.
- Scale: API is stateless apart from in-memory rate limits and WebSocket sessions; use sticky
  sessions for `/ws` and Redis for distributed limits.
- Time: UTC in storage; reports use `Asia/Kolkata`.

## Known environment traps

1. The manual dev default DB port is `5432`; the audit local DB was on `5433`. Use the JDBC URL
   that matches your container.
2. `MEDIDROP_SEED_ENABLED=true` skips seeding if any users already exist.
3. `SPRING_PROFILES_ACTIVE=prod` disables seeding, OpenAPI and the sandbox payment provider.
4. Prescriptions are stored outside the database. Back up both the DB and the storage volume.
5. The current audit process on `:8081` is not the production port; use `:8080` for the normal app.
