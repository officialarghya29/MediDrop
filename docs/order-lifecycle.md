# Order lifecycle

The state machine in `OrderStateMachine` is the single source of truth. The API rejects unknown edges
with `409 Conflict`, and rejects transitions the caller's role may not perform with `403 Forbidden`.

## States

| State | Meaning |
| --- | --- |
| `CREATED` | Order recorded, nothing confirmed yet |
| `PAYMENT_PENDING` | Online payment initiated but not settled |
| `PAYMENT_CONFIRMED` | Payment captured or COD accepted |
| `PRESCRIPTION_REVIEW_PENDING` | Contains Rx items; waiting for a pharmacist |
| `PRESCRIPTION_APPROVED` | Pharmacist approved; pharmacy may accept |
| `PRESCRIPTION_REJECTED` | Pharmacist rejected; refund path |
| `PHARMACY_ACCEPTED` | Pharmacy acknowledged the order |
| `PREPARING` | Items being packed |
| `READY_FOR_PICKUP` | Awaiting a delivery partner |
| `DELIVERY_ASSIGNED` | Partner assigned |
| `PICKED_UP` | Partner collected the package |
| `OUT_FOR_DELIVERY` | En route to the customer |
| `DELIVERED` | Handover OTP verified |
| `CANCELLATION_REQUESTED` | Customer asked to cancel after preparation started |
| `CANCELLED` | Cancelled before delivery |
| `REFUND_PENDING` / `REFUNDED` | Money reversal in progress / complete |
| `FAILED` | Payment or delivery failure |

## Allowed transitions

```text
CREATED ──► PAYMENT_PENDING ──► PAYMENT_CONFIRMED ──► PHARMACY_ACCEPTED ──► PREPARING ──► READY_FOR_PICKUP
   │               │                    │                    │                 │              │
   │               │                    ├──► PRESCRIPTION_REVIEW_PENDING ──► PRESCRIPTION_APPROVED
   │               │                    │                    │                 │
   ▼               ▼                    ▼                    ▼                 ▼
CANCELLED      CANCELLED/FAILED     CANCELLED        PRESCRIPTION_REJECTED   CANCELLATION_REQUESTED
                                                              │
READY_FOR_PICKUP ──► DELIVERY_ASSIGNED ──► PICKED_UP ──► OUT_FOR_DELIVERY ──► DELIVERED
                            │                  │               │
                            ▼                  ▼               ▼
                     CANCELLATION_REQUESTED  FAILED          FAILED

DELIVERED / CANCELLED / FAILED ──► REFUND_PENDING ──► REFUNDED
CANCELLATION_REQUESTED ──► CANCELLED ──► REFUND_PENDING
```

Actor rules (enforced per edge):

- **Customer** — cancel before preparation; request cancellation while preparing, ready or assigned.
- **Pharmacy** — accept, prepare, ready for pickup, approve/reject prescriptions, approve cancellation
  requests, cancel an accepted order.
- **Delivery partner** — pick up, out for delivery, delivered, failed.
- **Admin** — oversight, manual transitions, refunds, delivery assignment. Server flows reuse the same
  edges through `assertSystemAllowed`, which validates the edge but not the actor.

## Payment rules

- `COD` confirms the order without a gateway call.
- Online methods create a provider order and return a checkout session. The order stays
  `PAYMENT_PENDING` until the gateway signature is verified; a failed verification leaves it pending so
  the customer can retry.
- The sandbox provider simulates capture but refuses to operate under the `prod` profile. Its payments are
  stored with `is_test=true`, and reporting can distinguish them.
- Webhooks are idempotent: replaying a captured-payment event does not double-credit or re-transition.
- Refunds require `REFUND_PENDING`; the gateway reference is stored before the order is marked `REFUNDED`.

## Prescription gate

An order containing any `prescriptionRequired` item must reference an approved prescription owned by the
customer and linked to the fulfilling pharmacy. The check occurs again at pharmacy acceptance, so a
prescription approved after placement cannot bypass review. Rejection moves the order to
`PRESCRIPTION_REJECTED`; only an administrator can cancel or refund from there.

## Cancellation and refunds

| Situation | Outcome |
| --- | --- |
| Before preparation | Immediate `CANCELLED`; captured payment queued for refund |
| After preparation starts | `CANCELLATION_REQUESTED`; pharmacy accepts or rejects |
| After delivery | No cancellation; support raises a refund case |
| Pharmacy cancels | `CANCELLED` with a reason shown to the customer |
| Delivery fails | `FAILED`, then `REFUND_PENDING` by an administrator |

Every transition appends an immutable history row with `fromStatus`, `toStatus`, `actorRole`, `reason` and
timestamp. Customers see this timeline on the order page.

## SLA signals

- ETA is calculated from pharmacy prep time plus Haversine distance at an assumed city speed.
- Orders waiting too long in `READY_FOR_PICKUP` surface in the admin console for manual assignment.
- Delivery OTP attempts are stored in the assignment record; repeated failures are visible to operations.
