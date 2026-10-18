#!/usr/bin/env bash
# End-to-end demo against the gateway. Requires curl and jq.
#   ./scripts/demo.sh [base-url]
set -euo pipefail
BASE="${1:-http://localhost:8080}"
PASS='Password@123'

echo "== 1. Register + login"
curl -s -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"demo\",\"password\":\"$PASS\"}" | jq . || true
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"demo\",\"password\":\"$PASS\"}" | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"

echo "== 2. Browse products (first call hits PostgreSQL, repeat calls hit Redis)"
curl -s "$BASE/api/products" -H "$AUTH" | jq .

echo "== 3. Happy path: 1 smartphone (25,000)"
ORDER=$(curl -s -X POST "$BASE/api/orders" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":2,"quantity":1}]}')
echo "$ORDER" | jq .
ID=$(echo "$ORDER" | jq -r .id)
sleep 3
echo "-- order after saga:"
curl -s "$BASE/api/orders/$ID" -H "$AUTH" | jq '{id,status,totalAmount}'
curl -s "$BASE/api/payments/order/$ID" -H "$AUTH" | jq '{orderId,status,amount}'

echo "== 4. Compensation path: 2 laptops (150,000 > 100,000 payment limit)"
ORDER=$(curl -s -X POST "$BASE/api/orders" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":1,"quantity":2}]}')
ID=$(echo "$ORDER" | jq -r .id)
sleep 4
curl -s "$BASE/api/orders/$ID" -H "$AUTH" | jq '{id,status,failureReason}'

echo "== 5. Out-of-stock path: 9999 keyboards"
ORDER=$(curl -s -X POST "$BASE/api/orders" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":4,"quantity":9999}]}')
ID=$(echo "$ORDER" | jq -r .id)
sleep 3
curl -s "$BASE/api/orders/$ID" -H "$AUTH" | jq '{id,status,failureReason}'

echo "== 6. Security checks"
echo -n "no token      -> "; curl -s -o /dev/null -w '%{http_code}\n' "$BASE/api/orders"
echo -n "user creates product -> "; curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/products" \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"sku":"X","name":"X","price":1,"availableQuantity":1}'
