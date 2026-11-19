#!/usr/bin/env bash
# MediDrop end-to-end API smoke test.
# Requires the API running with the dev profile and demo data seeded.
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
pass() { echo "  ✓ $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗ $1"; FAIL=$((FAIL + 1)); }
expect_eq() { if [ "$1" = "$2" ]; then pass "$3 ($1)"; else fail "$3: expected '$2', got '$1'"; fi; }

jget() {
  python3 -c '
import json,sys
data=json.load(sys.stdin)
try:
    print(eval("data"+sys.argv[1]))
except Exception:
    print("")
' "$1"
}

login() {
  curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"identifier\":\"$1\",\"password\":\"$2\"}" | jget "['data']['accessToken']"
}

say "1. Health & public catalogue"
HEALTH=$(curl -s "$BASE/actuator/health" | jget "['status']")
expect_eq "$HEALTH" "UP" "API health"
MEDICINE_JSON=$(curl -s "$BASE/api/v1/medicines/search?q=Paracetamol&lat=22.5726&lng=88.3639")
MEDICINE_ID=$(printf '%s' "$MEDICINE_JSON" | jget "['data']['items'][0]['id']")
expect_eq "$(printf '%s' "$MEDICINE_JSON" | jget "['data']['totalElements']")" "2" "Search finds demo paracetamol"
NEARBY=$(curl -s "$BASE/api/v1/pharmacies/nearby?lat=22.5726&lng=88.3639&radiusKm=10" | jget "['data'][0]['id']")
[ -n "$NEARBY" ] && pass "Nearby pharmacy discovery" || fail "Nearby pharmacy discovery"
PROFILE=$(curl -s "$BASE/api/v1/medicines/$MEDICINE_ID?lat=22.5726&lng=88.3639")
INVENTORY_ID=$(printf '%s' "$PROFILE" | jget "['data']['availability'][0]['inventoryId']")
[ -n "$INVENTORY_ID" ] && pass "Availability exposes purchasable inventory" || fail "Availability inventory id"
CATEGORIES=$(curl -s "$BASE/api/v1/medicines/categories" | jget "['data']")
echo "$CATEGORIES" | grep -q "Prescription" && pass "Categories listed" || fail "Categories listed"

say "2. Role-based access control"
CUSTOMER_TOKEN=$(login customer@medidrop.local 'Demo@12345')
[ -n "$CUSTOMER_TOKEN" ] && pass "Customer login" || fail "Customer login"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/admin/dashboard" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
expect_eq "$CODE" "403" "Customer blocked from admin API"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/customer/me")
expect_eq "$CODE" "401" "Anonymous blocked from customer API"

say "3. Address, cart and COD order"
ADDRESS_ID=$(curl -s -X POST "$BASE/api/v1/customer/addresses" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"label":"Home","recipientName":"Demo Customer","phone":"9800000002",
       "line1":"DN 51, Sector V","line2":"Near City Centre","city":"Kolkata",
       "state":"West Bengal","pincode":"700091","latitude":22.5726,"longitude":88.3639,
       "defaultAddress":true}' | jget "['data']['id']")
[ -n "$ADDRESS_ID" ] && pass "Address saved" || fail "Address saved"
CART=$(curl -s -X POST "$BASE/api/v1/cart/items" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"inventoryId\":$INVENTORY_ID,\"quantity\":2}")
expect_eq "$(printf '%s' "$CART" | jget "['data']['itemCount']")" "2" "Cart accepts items"
SUBTOTAL=$(printf '%s' "$CART" | jget "['data']['subtotal']")
[ -n "$SUBTOTAL" ] && pass "Server computed cart subtotal ($SUBTOTAL)" || fail "Cart subtotal"

ORDER_JSON=$(curl -s -X POST "$BASE/api/v1/orders" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' -H "X-Idempotency-Key: smoke-cod-$(date +%s)" \
  -d "{\"addressId\":$ADDRESS_ID,\"paymentMethod\":\"COD\",\"note\":\"Smoke test order\"}")
ORDER_ID=$(printf '%s' "$ORDER_JSON" | jget "['data']['id']")
ORDER_NUMBER=$(printf '%s' "$ORDER_JSON" | jget "['data']['orderNumber']")
expect_eq "$(printf '%s' "$ORDER_JSON" | jget "['data']['status']")" "PAYMENT_CONFIRMED" "COD order confirmed"
[ -n "$ORDER_ID" ] && pass "Order created ($ORDER_NUMBER)" || fail "Order created"

say "4. Pharmacy fulfilment"
PHARMACY_TOKEN=$(login pharmacy@medidrop.local 'Demo@12345')
for STATUS in PHARMACY_ACCEPTED PREPARING READY_FOR_PICKUP; do
  RESULT=$(curl -s -X PATCH "$BASE/api/v1/pharmacy/orders/$ORDER_ID/status" \
    -H "Authorization: Bearer $PHARMACY_TOKEN" -H 'Content-Type: application/json' \
    -d "{\"status\":\"$STATUS\"}")
  expect_eq "$(printf '%s' "$RESULT" | jget "['data']['status']")" "$STATUS" "Pharmacy transition $STATUS"
done

say "5. Delivery assignment and OTP completion"
ADMIN_TOKEN=$(login admin@medidrop.local 'Admin@12345')
[ -n "$ADMIN_TOKEN" ] && pass "Admin login" || fail "Admin login"
ASSIGN=$(curl -s -X POST "$BASE/api/v1/admin/orders/$ORDER_ID/assign-delivery" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
echo "$ASSIGN" | grep -q '"success":true' && pass "Delivery partner auto-assigned" || fail "Delivery auto-assign"

DELIVERY_TOKEN=$(login delivery@medidrop.local 'Demo@12345')
ASSIGNMENT_ID=$(curl -s "$BASE/api/v1/delivery/assignments" -H "Authorization: Bearer $DELIVERY_TOKEN" \
  | jget "['data'][0]['id']")
[ -n "$ASSIGNMENT_ID" ] && pass "Assignment visible to partner" || fail "Assignment visible to partner"
curl -s -X POST "$BASE/api/v1/delivery/assignments/$ASSIGNMENT_ID/accept" \
  -H "Authorization: Bearer $DELIVERY_TOKEN" > /dev/null
for STATUS in AT_PHARMACY PICKED_UP OUT_FOR_DELIVERY; do
  RESULT=$(curl -s -X PATCH "$BASE/api/v1/delivery/assignments/$ASSIGNMENT_ID/status" \
    -H "Authorization: Bearer $DELIVERY_TOKEN" -H 'Content-Type: application/json' \
    -d "{\"status\":\"$STATUS\"}")
  expect_eq "$(printf '%s' "$RESULT" | jget "['data']['status']")" "$STATUS" "Delivery transition $STATUS"
done
OTP=$(curl -s "$BASE/api/v1/notifications" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  | python3 -c '
import json,sys,re
data=json.load(sys.stdin)["data"]
for n in data:
    m=re.search(r"verification code is (\d{4})", n.get("body",""))
    if m:
        print(m.group(1)); break
')
[ -n "$OTP" ] && pass "Customer received delivery OTP" || fail "Customer received delivery OTP"
COMPLETE=$(curl -s -X POST "$BASE/api/v1/delivery/assignments/$ASSIGNMENT_ID/complete" \
  -H "Authorization: Bearer $DELIVERY_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"otp\":\"$OTP\",\"proofSubmitted\":true}")
expect_eq "$(printf '%s' "$COMPLETE" | jget "['data']['status']")" "DELIVERED" "Delivery completed with OTP"
FINAL=$(curl -s "$BASE/api/v1/orders/$ORDER_ID" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  | jget "['data']['status']")
expect_eq "$FINAL" "DELIVERED" "Customer sees delivered order"

say "6. Online payment (sandbox) reconciliation"
curl -s -X POST "$BASE/api/v1/cart/items" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"inventoryId\":$INVENTORY_ID,\"quantity\":1}" > /dev/null
PAID_ORDER=$(curl -s -X POST "$BASE/api/v1/orders" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' -H "X-Idempotency-Key: smoke-upi-$(date +%s)" \
  -d "{\"addressId\":$ADDRESS_ID,\"paymentMethod\":\"UPI\"}")
PAID_ORDER_ID=$(printf '%s' "$PAID_ORDER" | jget "['data']['id']")
expect_eq "$(printf '%s' "$PAID_ORDER" | jget "['data']['status']")" "PAYMENT_PENDING" "Prepaid order awaits payment"
SESSION=$(curl -s -X POST "$BASE/api/v1/payments/create" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"orderId\":$PAID_ORDER_ID,\"method\":\"UPI\"}")
PROVIDER_ORDER_ID=$(printf '%s' "$SESSION" | jget "['data']['providerOrderId']")
DONE=$(curl -s -X POST "$BASE/api/v1/payments/sandbox/complete" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"providerOrderId\":\"$PROVIDER_ORDER_ID\"}")
expect_eq "$(printf '%s' "$DONE" | jget "['data']['status']")" "CAPTURED" "Sandbox payment captured"
expect_eq "$(printf '%s' "$DONE" | jget "['data']['test']")" "True" "Payment flagged as test"
sleep 1
PAID_STATUS=$(curl -s "$BASE/api/v1/orders/$PAID_ORDER_ID" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  | jget "['data']['status']")
expect_eq "$PAID_STATUS" "PAYMENT_CONFIRMED" "Order reconciled after payment"

say "7. Admin reporting from live data"
DASH=$(curl -s "$BASE/api/v1/admin/dashboard" -H "Authorization: Bearer $ADMIN_TOKEN")
[ "$(printf '%s' "$DASH" | jget "['data']['totalOrders']")" -ge 2 ] \
  && pass "Admin dashboard reflects real orders" || fail "Admin dashboard order count"
REPORT=$(curl -s "$BASE/api/v1/admin/reports/platform?days=7" -H "Authorization: Bearer $ADMIN_TOKEN")
[ -n "$(printf '%s' "$REPORT" | jget "['data']['grossRevenue']")" ] \
  && pass "Platform report generated" || fail "Platform report generated"

printf '\n\033[1mResult: %d passed, %d failed\033[0m\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
