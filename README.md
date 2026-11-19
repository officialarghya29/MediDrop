<div align="center">

<img src="frontend/public/logo.svg" alt="MediDrop" width="88" height="88" />

# MediDrop

**Hyperlocal medicine delivery for India — licensed pharmacies, pharmacist-reviewed prescriptions, live tracking.**

[![CI](https://github.com/officialarghya29/MediDrop/actions/workflows/ci.yml/badge.svg)](https://github.com/officialarghya29/MediDrop/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-0b8b68)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-0b8b68)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-17313d)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-17313d)](https://www.postgresql.org/)

</div>

MediDrop connects customers in Kolkata with nearby licensed pharmacies. Customers search live stock, upload
prescriptions for pharmacist review, pay online or on delivery, and follow the rider in real time. Pharmacies
manage inventory and fulfilment; delivery partners get nearby assignments; administrators govern approvals,
refunds and platform configuration.

> MediDrop does not provide medical advice or substitute prescribed medicines. Prescription-only medicines
> are dispensed only after review by a registered pharmacist.

---

## Contents

- [Feature overview](#feature-overview)
- [Architecture](#architecture)
- [Quick start](#quick-start)
- [Demo accounts](#demo-accounts)
- [Configuration](#configuration)
- [Testing](#testing)
- [Security](#security)
- [Project structure](#project-structure)
- [Documentation](#documentation)
- [External integrations](#external-integrations)
- [Roadmap](#roadmap)

---

## Feature overview

### Customer
- Search by brand, generic name or salt with fuzzy matching and “did you mean” suggestions.
- Live per-pharmacy pricing, stock and distance; prescription (Rx) and OTC labels.
- Single-pharmacy cart with server-side revalidation of stock, radius, minimum order and pharmacy status.
- Secure prescription upload (PDF/JPEG/PNG, 8 MB limit) with pharmacist decision and reviewer comments.
- Checkout with saved addresses, coupons, COD and online payment; idempotent order placement.
- Live order timeline, rider details, handover OTP, public tracking link and printable invoice.
- Cancellation/refund requests, reorder, notifications and post-delivery reviews.
- Profile, address book, support tickets and data-deletion request.

### Pharmacy
- Onboarding with drug licence, GSTIN and document uploads; admin approval before activation.
- Dashboard with pending orders, prescription queue, low stock and 30-day revenue.
- Order queue enforcing the lifecycle: accept → prepare → ready for pickup.
- Inventory with prices, stock ledger, batches/expiry and low-stock thresholds.
- Prescription review with approve/reject, verified items and comments (audited).
- Analytics: daily orders, top products, AOV and a 7-day restock forecast.

### Delivery partner
- Application with ID proof and payout details; approval required.
- Online/offline availability and live location publishing.
- Offer accept/decline, pickup and delivery progress, OTP-verified handover.
- Earnings for the day and lifetime, plus assignment history.

### Administrator
- Platform metrics, order trend, cancellation rate, average delivery time and top performers.
- Pharmacy and delivery-partner approvals and suspensions.
- Order oversight, manual transitions, delivery assignment and gateway refunds.
- User directory with suspension/restore, audit log and support queue.
- Runtime settings, coupons and medicine categories.

### Platform
- JWT access tokens with rotating, hashed refresh tokens; Argon2id password hashing; role-based access.
- Rate limiting on login, OTP and password reset.
- Append-only audit trail for governance actions.
- Flyway-managed schema with performance and trigram search indexes.
- Sandbox payment provider with real HMAC verification, refusing production use.
- Maps abstraction: Haversine distance by default, Google geocoding when configured.

---

## Architecture

```text
React 18 + TypeScript (Vite, Tailwind)
        │  /api/v1 (JSON)           /ws (STOMP)
        ▼
nginx  ── SPA + reverse proxy + TLS
        ▼
Spring Boot 3.3.5 (Java 21) ── modular monolith
  auth · catalogue · pharmacy · inventory · cart · order
  prescription · payment · delivery · admin · reporting · audit
        │
        ├── PostgreSQL 16 (Flyway, pg_trgm)
        └── Private prescription storage (FileStorage abstraction)
```

Cross-module references are scalar IDs, keeping queries explicit. The order state machine is the single
source of truth for lifecycle rules, and every transition is authorised per role. See
[docs/architecture.md](docs/architecture.md) for boundaries, data consistency and the security model.

### Tech stack

| Layer | Choice |
| --- | --- |
| API | Java 21, Spring Boot 3.3.5, Spring Security, Spring Data JPA, Bean Validation, Springdoc |
| Database | PostgreSQL 16, Flyway, H2 for tests |
| Auth | Argon2id, JJWT (HS256), opaque rotating refresh tokens |
| Web | React 18, TypeScript 5.6, Vite 5, Tailwind CSS 3, TanStack Query 5, React Router 6, Zustand, React Hook Form + Zod |
| Realtime | STOMP over WebSocket (SockJS-free native client) |
| Delivery | Docker multi-stage images, nginx, GitHub Actions |

---

## Quick start

### Option A — Docker Compose (recommended)

```bash
cp .env.example .env
# set MEDIDROP_JWT_SECRET (openssl rand -base64 48) and passwords in .env
docker compose up -d --build
docker compose ps
```

- Web: <http://localhost:3000>
- API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>

The compose stack runs in the `prod` profile with demo seeding disabled, so create the first administrator
deliberately or enable seeding only in a disposable environment.

### Option B — Local development

**Prerequisites:** Java 21, Maven 3.9+, Node 20+, PostgreSQL 16.

```bash
# 1. Database
createdb medidrop
# or: docker run -d --name medidrop-pg -e POSTGRES_DB=medidrop \
#      -e POSTGRES_USER=medidrop -e POSTGRES_PASSWORD=medidrop -p 5432:5432 postgres:16-alpine

# 2. API (dev profile seeds a demo city)
cd backend
MEDIDROP_JWT_SECRET=dev-only-medidrop-secret-change-before-deploy-9f3c1a \
MEDIDROP_SEED_ENABLED=true \
mvn spring-boot:run

# 3. Web
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /api and /ws to :8080
```

Flyway applies migrations automatically on first boot. The dev profile seeds categories, medicines,
pharmacies, users and demo orders — all flagged as demo.

### Useful commands

```bash
cd backend && mvn test                 # 16 tests
cd backend && mvn package              # production jar
cd frontend && npm test                # 13 tests
cd frontend && npm run build           # typecheck + bundle
infrastructure/scripts/smoke-test.sh    # 31 live API checks
```

---

## Demo accounts

Seeded only when `MEDIDROP_SEED_ENABLED=true` (dev profile default). Demo records are clearly flagged and
must never be treated as production data.

| Role | Email | Password |
| --- | --- | --- |
| Administrator | `admin@medidrop.local` | `Admin@12345` |
| Customer | `customer@medidrop.local` | `Demo@12345` |
| Pharmacy 1 | `pharmacy@medidrop.local` | `Demo@12345` |
| Pharmacy 2 | `pharmacy2@medidrop.local` | `Demo@12345` |
| Delivery partner 1 | `delivery@medidrop.local` | `Demo@12345` |
| Delivery partner 2 | `delivery2@medidrop.local` | `Demo@12345` |

Passwords are hashed with Argon2id at seed time. Change the admin password immediately outside a
disposable environment.

---

## Configuration

All configuration is environment-driven; secrets never live in the repository. Key variables:

| Variable | Default | Notes |
| --- | --- | --- |
| `MEDIDROP_DB_URL` | `jdbc:postgresql://localhost:5432/medidrop` | JDBC URL |
| `MEDIDROP_DB_USERNAME` / `MEDIDROP_DB_PASSWORD` | `medidrop` | Use strong credentials |
| `MEDIDROP_JWT_SECRET` | dev-only value in `dev` | **Required in prod**, ≥32 random chars |
| `MEDIDROP_CORS_ORIGINS` | `http://localhost:5173` | Comma-separated origins |
| `MEDIDROP_STORAGE_PROVIDER` / `_PATH` | `local`, `./storage/prescriptions` | Persistent volume required |
| `MEDIDROP_PAYMENTS_PROVIDER` | `sandbox` | `razorpay` for live |
| `RAZORPAY_KEY_ID` / `_KEY_SECRET` / `_WEBHOOK_SECRET` | — | Live gateway credentials |
| `MEDIDROP_MAPS_PROVIDER` | `haversine` | `google` enables geocoding |
| `MEDIDROP_MAIL_PROVIDER` / `MEDIDROP_SMS_PROVIDER` | `console` | SMTP when configured |
| `MEDIDROP_SEED_ENABLED` | `true` in dev | Never in production |
| `VITE_API_URL` / `VITE_WS_URL` | same-origin | Frontend build-time settings |
| `VITE_GOOGLE_MAPS_API_KEY` | — | Browser-restricted map key |

The complete list, including production checklist, is in [docs/deployment.md](docs/deployment.md) and
[.env.example](.env.example).

---

## Testing

| Suite | Result in this repository |
| --- | --- |
| `cd backend && mvn test` | 16/16 passing |
| `cd frontend && npm test` | 13/13 passing |
| `cd frontend && npm run build` | Typecheck + production bundle succeed |
| `infrastructure/scripts/smoke-test.sh` | 31/31 checks passing against a live API |

The smoke test covers catalogue, RBAC (401/403), COD ordering, pharmacy transitions, auto-assignment, OTP
handover, sandbox payment reconciliation and admin reporting. Details and gaps are in
[docs/testing.md](docs/testing.md).

---

## Security

- **Passwords:** Argon2id, per-user salt.
- **Sessions:** 15-minute JWT access tokens; refresh tokens are opaque, hashed at rest and rotated on use.
- **Authorisation:** role checks at the endpoint plus ownership/tenancy checks in services.
- **Prescriptions:** private storage; downloads are authenticated and scoped to owner, linked pharmacy or admin.
- **Payments:** gateway signatures verified server-side; webhooks are replay-safe; sandbox payments are flagged test-only.
- **Abuse:** rate limits on login, OTP and reset; generic error messages; no stack traces in responses.
- **Governance:** append-only audit trail, admin-only approvals and refunds.
- **Transport:** TLS termination and security headers via nginx.

Known trade-off: the SPA keeps tokens in `localStorage` to keep the API stateless. Short token lifetimes and
refresh rotation reduce exposure; cookie-based sessions with CSRF protection are the alternative for stricter
environments. See [docs/architecture.md](docs/architecture.md#security-model).

---

## Project structure

```text
MediDrop/
├── backend/
│   ├── src/main/java/com/medidrop/       # auth, medicine, pharmacy, inventory, cart,
│   │                                     # order, prescription, payment, delivery,
│   │                                     # admin, reporting, notification, support, audit
│   ├── src/main/resources/db/migration/  # V1 schema, V2 indexes, V3 trigram search
│   ├── src/test/java/com/medidrop/       # state machine, pricing, auth, payment tests
│   └── Dockerfile
├── frontend/
│   ├── src/components/                   # shell, UI primitives, map, cards
│   ├── src/features/                     # auth, customer, pharmacy, delivery, admin
│   ├── src/lib/ src/store/ src/hooks/    # API client, formatting, auth store, geolocation
│   ├── src/test/                         # vitest setup and tests
│   ├── nginx.conf
│   └── Dockerfile
├── docs/                                 # architecture, API, lifecycle, deployment, testing
├── infrastructure/scripts/smoke-test.sh
├── .github/workflows/ci.yml
├── docker-compose.yml
└── .env.example
```

### API at a glance

Base path `/api/v1`, envelope `{ success, message, data, timestamp }`. OpenAPI is served at
`/swagger-ui.html`. The full endpoint catalogue is in [docs/api.md](docs/api.md).

---

## Documentation

| Document | Contents |
| --- | --- |
| [Architecture](docs/architecture.md) | Modules, boundaries, consistency, security, integration seams |
| [Order lifecycle](docs/order-lifecycle.md) | States, transitions, roles, payment and refund rules |
| [API reference](docs/api.md) | Conventions and endpoint catalogue |
| [Deployment](docs/deployment.md) | Docker, environment matrix, operations, go-live checklist |
| [Testing](docs/testing.md) | Test layers, commands, coverage and known gaps |

---

## External integrations

| Integration | Status in this repository |
| --- | --- |
| Razorpay payments | Implementation present with HMAC verification; **live keys not provided, not verified against the gateway** |
| Sandbox payments | Working end-to-end, refuses to run in `prod`, marks payments `is_test` |
| Google Maps geocoding | Implementation present; **not verified without a key**. Default Haversine provider measures distance only and refuses geocoding |
| Browser map | Google Maps JS when `VITE_GOOGLE_MAPS_API_KEY` is set; otherwise an honest coordinate panel with an OpenStreetMap link |
| Email | Console provider; SMTP implementation present, **not verified without credentials** |
| SMS / OTP | Console provider only; **no SMS gateway integrated** |
| OCR | Interface present, disabled by default; pharmacist review never depends on it |
| Object storage | Local disk behind `FileStorage`; **S3 adapter not implemented** |
| Push notifications | Not implemented |

No integration above is claimed as working without credentials in this environment.

---

## Roadmap

- Live Razorpay certification and reconciliation reports.
- SMS OTP gateway and templated transactional email.
- S3-compatible prescription storage with lifecycle policies.
- OCR-assisted pharmacist review (human approval remains mandatory).
- Redis-backed rate limiting and WebSocket fan-out for multi-instance deployments.
- Regional expansion beyond Kolkata with per-city catalogues and delivery zones.
- Accessibility audit and Hindi/Bengali localisation.

---

<div align="center">
<sub>Built for hyperlocal healthcare in India · Kolkata, West Bengal</sub>
</div>
