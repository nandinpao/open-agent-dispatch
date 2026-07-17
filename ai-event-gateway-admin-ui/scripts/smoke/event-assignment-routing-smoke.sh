#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
COUNT="${1:-3}"
"$(dirname "$0")/agent-register-smoke.sh" agent-tnn-001 TNN gateway-tnn-001 >/dev/null
for i in $(seq 1 "$COUNT"); do
  curl -sS -X POST "$BASE_URL/api/events/intake" \
    -H 'Content-Type: application/json' \
    -d '{
      "tenantId":"tenant-a",
      "sourceSystem":"MES",
      "siteId":"TNN",
      "plantId":"TNN-FAB-01",
      "objectType":"EQUIPMENT",
      "objectId":"EQP-1001",
      "eventType":"EQUIPMENT_ALARM",
      "errorCode":"TEMP_HIGH",
      "severity":"HIGH",
      "message":"Chamber temperature over threshold",
      "attributes":{"line":"L1"}
    }'
  echo
  sleep 0.1
done
printf '\nAssignments:\n'
curl -sS "$BASE_URL/api/assignments?limit=10"
printf '\nRouting decisions:\n'
curl -sS "$BASE_URL/api/routing-decisions?limit=10"
echo
