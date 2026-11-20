# API reference

Base path: `/api/v1` · OpenAPI UI: `/swagger-ui.html` · Health: `/actuator/health`

## Conventions

Every JSON response uses the envelope:

```json
{ "success": true, "message": "Order placed", "data": { }, "timestamp": "2026-11-19T10:15:30Z" }
```

Validation failures return `400` with a `data` object of `field → message`. Authorisation failures return
`401` (missing/expired token) or `403` (wrong role or not the resource owner). Business conflicts such as
an illegal status transition return `409`. Errors never include stack traces in production.

- **Auth**: `Authorization: Bearer <access token>`. Access tokens live 15 minutes; refresh tokens rotate.
- **Pagination**: `page` (0-based), `size`; responses contain `content`, `totalElements`, `totalPages`.
- **Idempotency**: `POST /orders` accepts `X-Idempotency-Key`; replaying the same key returns the original
  order instead of creating a duplicate.
- **Uploads**: `multipart/form-data`, PDF/JPEG/PNG, maximum 8 MB.
- **Time**: ISO-8601 UTC in responses.

## Public

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | Customer registration |
| POST | `/auth/login` | Login with email or mobile |
| POST | `/auth/refresh` | Rotate refresh token |
| POST | `/auth/logout` | Revoke refresh token |
| POST | `/auth/otp/request` | Send verification/reset OTP |
| POST | `/auth/otp/verify` | Verify OTP and issue session |
| POST | `/auth/forgot-password` | Request password reset |
| POST | `/auth/reset-password` | Consume reset token |
| GET | `/medicines` | Browse catalogue |
| GET | `/medicines/search` | Search with category, pharmacy, sort and Rx filters |
| GET | `/medicines/suggestions` | Autocomplete suggestions |
| GET | `/medicines/categories` | Categories |
| GET | `/medicines/manufacturers` | Manufacturers |
| GET | `/medicines/{id}` | Detail with nearby availability (`lat`, `lng`) |
| GET | `/medicines/{id}/substitutes` | Lower-cost equivalents at a pharmacy (`pharmacyId`, `limit`) |
| GET | `/pharmacies/nearby` | Pharmacies within radius |
| GET | `/pharmacies/{id}` | Pharmacy profile |
| POST | `/pharmacies/register` | Pharmacy application |
| POST | `/delivery/register` | Delivery partner application |
| GET | `/tracking/{key}` | Public, token-scoped delivery status |

## Customer (`CUSTOMER`)

| Method | Path | Purpose |
| --- | --- | --- |
| GET/PUT | `/customer/me` | Read/update profile |
| POST | `/customer/me/deletion-request` | Data deletion request |
| GET/POST | `/customer/addresses` | List/add addresses |
| PUT/DELETE | `/customer/addresses/{id}` | Update/remove address |
| GET | `/cart` | Cart with live pricing |
| POST | `/cart/items` | Add item |
| PATCH/DELETE | `/cart/items/{id}` | Change quantity/remove |
| POST | `/orders` | Place order (idempotent) |
| GET | `/orders` | List orders (`filter=all\|active\|completed`) |
| GET | `/orders/counts` | Status counts |
| GET | `/orders/{id}` | Order detail, history, partner, OTP |
| POST | `/orders/{id}/cancel` | Cancel or request cancellation |
| POST | `/orders/{id}/reorder` | Add available items to cart |
| GET | `/orders/{id}/invoice` | Structured invoice |
| POST | `/orders/{id}/review` | Rate a delivered order |
| GET/POST | `/customer/prescriptions` | List/upload prescriptions |
| GET | `/customer/prescriptions/{id}` | Prescription detail |
| GET | `/customer/prescriptions/{id}/file` | Stream private file |
| DELETE | `/customer/prescriptions/{id}` | Delete before review |
| POST | `/payments/create` | Create provider checkout session |
| POST | `/payments/verify` | Verify gateway signature |
| GET | `/payments/history` | Payment history |
| GET/POST | `/support` | List/create support tickets |
| GET | `/notifications` | Notifications |
| GET | `/notifications/unread-count` | Unread count |
| POST | `/notifications/{id}/read` | Mark read |

## Pharmacy (`PHARMACY`)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/pharmacy/profile` | Pharmacy profile and hours |
| GET | `/pharmacy/dashboard` | Counters and revenue |
| PUT | `/pharmacy/hours` | Opening hours |
| POST | `/pharmacy/accepting-orders` | Pause/resume orders |
| GET/POST | `/pharmacy/documents` | Compliance documents |
| GET | `/pharmacy/analytics` | Sales, top products, forecast |
| GET | `/pharmacy/orders` | Order queue (`status`) |
| GET | `/pharmacy/orders/{id}` | Order detail |
| PATCH | `/pharmacy/orders/{id}/status` | Lifecycle transition |
| GET | `/pharmacy/orders/{id}/invoice` | Invoice |
| GET/POST | `/pharmacy/inventory` | List/add listings |
| PATCH/DELETE | `/pharmacy/inventory/{id}` | Adjust/remove listing |
| GET/POST | `/pharmacy/inventory/{id}/batches` | Batches and expiry |
| GET | `/pharmacy/inventory/{id}/ledger` | Stock ledger |
| GET | `/pharmacy/inventory/low-stock` | Below threshold |
| GET | `/pharmacy/inventory/expiring` | Expiring batches |
| GET | `/pharmacy/prescriptions` | Review queue |
| GET | `/pharmacy/prescriptions/{id}/file` | Prescription file |
| GET | `/pharmacy/prescriptions/{id}/reviews` | Review history |
| POST | `/pharmacy/prescriptions/{id}/review` | Approve/reject |

## Delivery (`DELIVERY`)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/delivery/profile` | Partner profile |
| GET | `/delivery/dashboard` | Earnings and active runs |
| POST | `/delivery/availability` | Go online/offline |
| GET | `/delivery/assignments` | Assignments (`status`) |
| POST | `/delivery/assignments/{id}/accept` | Accept offer |
| POST | `/delivery/assignments/{id}/reject` | Decline offer |
| PATCH | `/delivery/assignments/{id}/status` | Pickup/delivery progress |
| POST | `/delivery/assignments/{id}/complete` | Complete with OTP |
| POST | `/delivery/location` | Publish live location |

## Admin (`ADMIN`)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/admin/dashboard` | Platform counters and trend |
| GET | `/admin/users` | User directory |
| GET | `/admin/users/{id}` | User detail |
| POST | `/admin/users/{id}/suspend` | Suspend/restore |
| GET | `/admin/pharmacies` | Pharmacy applications |
| POST | `/admin/pharmacies/{id}/decision` | Approve/reject |
| POST | `/admin/pharmacies/{id}/suspend` | Suspend pharmacy |
| GET | `/admin/delivery-partners` | Partner applications |
| POST | `/admin/delivery-partners/{id}/decision` | Approve/reject |
| POST | `/admin/delivery-partners/{id}/suspend` | Suspend partner |
| GET | `/admin/orders` | All orders |
| GET | `/admin/orders/{id}` | Order detail |
| PATCH | `/admin/orders/{id}/status` | Manual transition |
| POST | `/admin/orders/{id}/refund` | Refund |
| POST | `/admin/orders/{id}/assign-delivery` | Force assignment |
| GET | `/admin/refunds` | Refund ledger |
| GET | `/admin/audit-logs` | Audit trail |
| GET | `/admin/reports/platform` | Platform report |
| GET/POST/PUT | `/admin/categories` | Category management |
| GET/POST/PUT/DELETE | `/admin/coupons` | Coupon management |
| GET/PUT | `/admin/settings` | System settings |
| GET/POST | `/admin/support-tickets` | Support queue |

Webhook: `POST /payments/webhook` (public, HMAC-verified, no JWT).
