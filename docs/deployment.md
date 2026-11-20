# Deployment

## Recommended: Docker Compose

```bash
cp .env.example .env         # fill MEDIDROP_JWT_SECRET and database passwords
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8080/actuator/health
open http://localhost:3000
```

The stack contains PostgreSQL 16, the API container and nginx serving the SPA. nginx proxies `/api` and
`/ws` to the API, so the browser uses same-origin requests and no CORS configuration is needed for the
default deployment. Prescriptions live on the `storage` volume; the database on `db-data`.

Set `MEDIDROP_SEED_ENABLED=false` (the default under `prod`) in real environments. Demo records are flagged
as demo and must not be used as production data.

## Manual deployment

1. **Database** — PostgreSQL 16 with the `pg_trgm` extension available (Flyway V3 creates it).
2. **API** — `mvn -B package` then run with `SPRING_PROFILES_ACTIVE=prod` and the environment variables in
   the table below. Apply migrations automatically on boot (`spring.flyway.enabled=true`); take a backup
   before each release.
3. **Web** — `npm ci && npm run build`, serve `dist/` with nginx using `frontend/nginx.conf`, adjusting the
   `api:8080` upstream.
4. **TLS** — terminate HTTPS at your load balancer or nginx and redirect 80 → 443. The shipped nginx
   configuration sends HSTS once the site is served over HTTPS.

## Environment variables

| Variable | Required | Purpose |
| --- | --- | --- |
| `MEDIDROP_JWT_SECRET` | yes | HMAC key, at least 32 random characters; rotating it logs everyone out |
| `MEDIDROP_DB_URL` | yes | JDBC URL |
| `MEDIDROP_DB_USERNAME` / `MEDIDROP_DB_PASSWORD` | yes | Database credentials |
| `MEDIDROP_DB_POOL_SIZE` | no | Hikari pool size (default 20) |
| `MEDIDROP_CORS_ORIGINS` | yes for split origin | Comma-separated browser origins |
| `MEDIDROP_OPENAPI_ENABLED` | no | Expose Swagger/OpenAPI under the production profile (default false) |
| `MEDIDROP_PUBLIC_URL` | yes | Public API URL used in links |
| `MEDIDROP_FRONTEND_URL` | yes | Public web URL used in emails |
| `MEDIDROP_STORAGE_PATH` | yes with local storage | Upload directory (must be a persistent volume) |
| `MEDIDROP_STORAGE_PROVIDER` | no | `local` today; S3 adapter pending |
| `MEDIDROP_PAYMENTS_PROVIDER` | no | `sandbox` or `razorpay` |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` / `RAZORPAY_WEBHOOK_SECRET` | with razorpay | Gateway credentials |
| `MEDIDROP_MAPS_PROVIDER` | no | `haversine` (distance only) or `google` |
| `MEDIDROP_MAPS_API_KEY` | with google | Server-side geocoding key |
| `MEDIDROP_MAIL_PROVIDER` | no | `console` or `smtp` |
| `MEDIDROP_SMS_PROVIDER` | no | `console` until an SMS gateway is implemented |
| `MEDIDROP_SEED_ENABLED` | no | Demo data; never enable in production |
| `MEDIDROP_ADMIN_PASSWORD` | first boot only | Password for the seeded admin when seeding is enabled |
| `PORT` | no | API port, default 8080 |

## Operations

- **Health** — `GET /actuator/health` (liveness/readiness). The compose healthcheck uses it.
- **Logs** — JSON-friendly structured logs to stdout; ship them to your aggregator. Never log tokens,
  OTPs or prescription contents.
- **Backups** — nightly `pg_dump` plus a snapshot of the storage volume. Test restores quarterly.
- **Migrations** — Flyway runs before the API accepts traffic. Run a single instance at a time for
  migrations, or use a pre-deploy migration job.
- **Scaling** — the API is stateless apart from in-memory rate limiting and WebSocket sessions. Scale
  horizontally behind a load balancer with sticky sessions for `/ws`, and move rate limiting to Redis when
  running more than one instance.
- **Time zone** — store UTC (default); business reports use `Asia/Kolkata`.

## Email, SMS and maps

The console providers print messages to the API log in development. Before go-live:

1. Configure an SMTP provider and set `MEDIDROP_MAIL_PROVIDER=smtp` with credentials.
2. Implement and configure an SMS gateway adapter; until then, OTP delivery is log-only.
3. If geocoding is required, enable Google Maps with a server key and restrict it by IP/referrer. The
   default Haversine provider measures distance only and deliberately refuses geocoding.

## Go-live checklist

- [ ] `MEDIDROP_JWT_SECRET` generated from a CSPRNG and stored in a secret manager
- [ ] Database and storage backups verified with a restore drill
- [ ] `SPRING_PROFILES_ACTIVE=prod`, demo seeding disabled
- [ ] HTTPS everywhere; HSTS enabled; HTTP redirected
- [ ] Razorpay live keys and webhook secret configured, webhook endpoint reachable
- [ ] SMTP/SMS providers configured and tested with a real message
- [ ] Google Maps key (if used) restricted and billed
- [ ] Pharmacy licences verified manually; demo pharmacies removed
- [ ] Prescription storage volume encrypted at rest and included in backups
- [ ] Rate limits reviewed for expected traffic
- [ ] Monitoring/alerting on 5xx rate, latency, DB connections and failed webhooks
- [ ] Legal pages (privacy, terms, refund policy, grievance officer) published
- [ ] Incident contact and data-deletion process documented

## Verified vs pending

Verified locally: migrations on PostgreSQL 16 (V1–V7), API boot with seed data, 52 backend tests,
33-check API smoke test, frontend typecheck/build, 13 frontend tests, 9 Playwright/axe public and
authenticated checks, the 60-second search/order soak, 250K catalogue search p95 280.5 ms, and a
Lighthouse mobile median of 92/93 performance with accessibility 100. Multi-instance load, keyboard
and screen-reader passes, full page coverage and live providers are not verified.

Not verified (no credentials in this environment): live Razorpay payments and refunds, Google Maps
geocoding, SMTP delivery, SMS delivery, S3 storage, OCR. Their integrations are isolated behind
interfaces and disabled or simulated by default; treat them as pending until exercised in staging.
