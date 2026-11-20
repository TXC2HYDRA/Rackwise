#!/usr/bin/env bash
# Adds a few demo requests and proposed plans (as alice) to a running backend, so the reviewer queue is not empty.
# Usage: scripts/seed-demo.sh [base-url]   (default http://localhost:8080)
set -euo pipefail
base="${1:-http://localhost:8080}"; pw="${DEV_USER_PASSWORD:-Rackwise-Dev-1!}"
json() { python3 -c "import json,sys;print(json.load(sys.stdin)$1)"; }
token=$(curl -fs -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"alice\",\"password\":\"$pw\"}" | json "['accessToken']")
auth="Authorization: Bearer $token"
dc=$(curl -fs "$base/api/v1/inventory/datacenters" -H "$auth" | json "[0]['id']")
for spec in "40|Q4 launch capacity" "12|Analytics cluster refresh" "200|Region expansion (too large, shows a partial plan)"; do
  n=${spec%%|*}; why=${spec#*|}
  rid=$(curl -fs -X POST "$base/api/v1/capacity-requests" -H "$auth" -H 'Content-Type: application/json' -H "Idempotency-Key: demo-$(uuidgen)" \
    -d "{\"dataCenterId\":\"$dc\",\"allowOtherRackGroups\":true,\"serverCount\":$n,\"cpuCores\":32,\"ramGb\":256,\"storageTb\":8,\"powerWatts\":500,\"justification\":\"$why\",\"priority\":\"NORMAL\"}" | json "['id']")
  curl -fs -X POST "$base/api/v1/capacity-requests/$rid/plans" -H "$auth" >/dev/null
  echo "request $rid: $n servers, plan proposed"
done
