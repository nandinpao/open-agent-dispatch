#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
COUNT="${1:-3}"
AGENT_ID="${2:-agent-tnn-001}"
GATEWAY_NODE_ID="${3:-gateway-tnn-001}"

# Register an available Agent in core Agent Directory.
curl -sS -X POST "${BASE_URL}/api/agents/register" \
  -H 'Content-Type: application/json' \
  -d '{
    "agentId": "'"${AGENT_ID}"'",
    "agentType": "OPENCLAW",
    "ownerSite": "TNN",
    "ownerGatewayNodeId": "'"${GATEWAY_NODE_ID}"'",
    "status": "IDLE",
    "capabilities": ["incident-analysis", "mes-analysis"],
    "currentTaskCount": 0,
    "maxConcurrentTasks": 3,
    "healthScore": 96
  }' >/dev/null

for i in $(seq 1 "${COUNT}"); do
  curl -sS -X POST "${BASE_URL}/api/events/intake" \
    -H 'Content-Type: application/json' \
    -d '{
      "tenantId": "tenant-a",
      "sourceSystem": "MES",
      "siteId": "TNN",
      "plantId": "TNN-FAB-01",
      "objectType": "EQUIPMENT",
      "objectId": "EQP-1001",
      "eventType": "EQUIPMENT_ALARM",
      "errorCode": "TEMP_HIGH",
      "severity": "HIGH",
      "message": "Chamber temperature over threshold"
    }'
  echo
  sleep 0.1
done

echo "Approved dispatch requests:"
curl -sS "${BASE_URL}/api/dispatch-requests/status/APPROVED?limit=20"
echo

echo "Execute approved dispatch requests:"
curl -sS -X POST "${BASE_URL}/api/dispatch-requests/execute-approved?limit=20"
echo
