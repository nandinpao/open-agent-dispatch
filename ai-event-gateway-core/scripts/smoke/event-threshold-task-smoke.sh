#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
COUNT="${1:-30}"
for i in $(seq 1 "${COUNT}"); do
  curl -sS -X POST "${BASE_URL}/api/events/intake" \
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
      "message":"Chamber temperature over threshold"
    }' >/tmp/aeg-core-p3-last-response.json
  if [ "${i}" = "${COUNT}" ]; then
    cat /tmp/aeg-core-p3-last-response.json | jq .
  fi
 done
