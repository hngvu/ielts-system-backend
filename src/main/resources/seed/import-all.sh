#!/bin/bash
# Seed data import script
# Usage: ./import-all.sh [BASE_URL] [EMAIL] [PASSWORD]

BASE_URL="${1:-http://localhost:8080}"
EMAIL="${2:-admin@cap.vn}"
PASSWORD="${3:-admin123}"
SEED_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== IELTS Test Seed Data Importer ==="
echo "API: $BASE_URL"
echo ""

# Step 1: Login to get token
echo "[1/2] Logging in as $EMAIL..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/token" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['result']['token'])" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "ERROR: Login failed. Response: $LOGIN_RESPONSE"
  exit 1
fi
echo "Login successful! Token obtained."
echo ""

# Step 2: Import each seed file
echo "[2/2] Importing seed files..."
SUCCESS=0
FAIL=0

for file in "$SEED_DIR"/*.json; do
  filename=$(basename "$file")
  echo -n "  Importing $filename... "

  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/admin/tests/import" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d @"$file")

  HTTP_CODE=$(echo "$RESPONSE" | tail -1)
  BODY=$(echo "$RESPONSE" | sed '$d')

  if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "200" ]; then
    TEST_ID=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('result',''))" 2>/dev/null)
    echo "OK (testId: $TEST_ID)"
    SUCCESS=$((SUCCESS + 1))
  else
    echo "FAILED (HTTP $HTTP_CODE)"
    echo "    Response: $BODY"
    FAIL=$((FAIL + 1))
  fi
done

echo ""
echo "=== Import Complete ==="
echo "Success: $SUCCESS | Failed: $FAIL"
