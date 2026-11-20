#!/usr/bin/env bash
# Smoke test of a RUNNING backend (dev profile). Usage: scripts/smoke-test.sh [base-url]
set -euo pipefail
base="${1:-http://localhost:8080}"
pw="${DEV_USER_PASSWORD:-Rackwise-Dev-1!}"
fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }
curl -fs "$base/actuator/health" | grep -q '"UP"' || fail "health not UP"
code=$(curl -s -o /dev/null -w '%{http_code}' "$base/api/v1/inventory/capacity")
[ "$code" = 401 ] || fail "unauthenticated capacity returned $code, expected 401"
token=$(curl -fs -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"alice\",\"password\":\"$pw\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])') || fail "login failed"
curl -fs "$base/api/v1/inventory/capacity" -H "Authorization: Bearer $token" | grep -q totalSlots || fail "capacity summary missing"
code=$(curl -s -o /dev/null -w '%{http_code}' "$base/api/v1/users" -H "Authorization: Bearer $token")
[ "$code" = 403 ] || fail "requester on /users returned $code, expected 403"
echo "SMOKE OK ($base)"
