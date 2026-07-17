#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
# First create a medium incident below threshold.
curl -sS -X POST "${BASE_URL}/api/events/intake" \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId":"tenant-a",
    "sourceSystem":"MES",
    "siteId":"TPE",
    "plantId":"TPE-FAB-01",
    "objectType":"EQUIPMENT",
    "objectId":"EQP-2001",
    "eventType":"EQUIPMENT_ALARM",
    "errorCode":"PRESSURE_DROP",
    "severity":"MEDIUM",
    "message":"Pressure drop detected"
  }' >/tmp/aeg-core-p3-medium.json

# Then send the same fingerprint with critical severity to trigger escalation task.
curl -sS -X POST "${BASE_URL}/api/events/intake" \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId":"tenant-a",
    "sourceSystem":"MES",
    "siteId":"TPE",
    "plantId":"TPE-FAB-01",
    "objectType":"EQUIPMENT",
    "objectId":"EQP-2001",
    "eventType":"EQUIPMENT_ALARM",
    "errorCode":"PRESSURE_DROP",
    "severity":"CRITICAL",
    "message":"Pressure drop detected"
  }' | jq .
