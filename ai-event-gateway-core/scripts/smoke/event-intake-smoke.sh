#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
cat <<'JSON' | curl -sS -X POST "$BASE_URL/api/events/intake" \
  -H 'Content-Type: application/json' \
  --data-binary @- | python3 -m json.tool
{
  "tenantId": "tenant-a",
  "sourceSystem": "MES",
  "siteId": "TNN",
  "plantId": "TNN-FAB-01",
  "objectType": "EQUIPMENT",
  "objectId": "EQP-1001",
  "eventType": "EQUIPMENT_ALARM",
  "errorCode": "TEMP_HIGH",
  "severity": "HIGH",
  "message": "Chamber temperature over threshold",
  "attributes": {
    "temperature": 92.7,
    "threshold": 80
  }
}
JSON
