#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
COUNT="${1:-3}"
AGENT_ID="${2:-agent-tnn-001}"
GATEWAY_ID="${3:-gateway-tnn-001}"

echo "Registering mock agent ${AGENT_ID}..."
curl -sS -X POST "${BASE_URL}/api/agents/register" \
  -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"${AGENT_ID}\",\"agentType\":\"OPENCLAW\",\"ownerGatewayNodeId\":\"${GATEWAY_ID}\",\"siteId\":\"TNN\",\"siteName\":\"台南機房\",\"status\":\"IDLE\",\"capabilities\":[\"mes-analysis\",\"issue-tracking\"]}" >/dev/null

for i in $(seq 1 "${COUNT}"); do
  echo "Submitting duplicate event ${i}/${COUNT}..."
  curl -sS -X POST "${BASE_URL}/api/events/intake" \
    -H 'Content-Type: application/json' \
    -d '{"tenantId":"tenant-a","sourceSystem":"MES","siteId":"TNN","plantId":"TNN-FAB-01","objectType":"EQUIPMENT","objectId":"EQP-1001","eventType":"EQUIPMENT_ALARM","errorCode":"TEMP_HIGH_P10","severity":"CRITICAL","message":"Chamber temperature over threshold for P10 adapter executor smoke"}' >/tmp/aeg-core-p10-event-response.json
  cat /tmp/aeg-core-p10-event-response.json
  echo
  sleep 0.2
done

TASK_ID=$(python3 - <<'PY'
import json
try:
    envelope=json.load(open('/tmp/aeg-core-p10-event-response.json'))
response=envelope.get('data', envelope) if isinstance(envelope, dict) else envelope
print(response.get('taskId') or '')
except Exception:
    print('')
PY
)
DISPATCH_ID=$(python3 - <<'PY'
import json
try:
    envelope=json.load(open('/tmp/aeg-core-p10-event-response.json'))
response=envelope.get('data', envelope) if isinstance(envelope, dict) else envelope
print(response.get('dispatchRequestId') or '')
except Exception:
    print('')
PY
)

if [[ -n "${DISPATCH_ID}" ]]; then
  echo "Approving and executing dispatch request ${DISPATCH_ID}..."
  curl -sS -X POST "${BASE_URL}/api/dispatch-requests/${DISPATCH_ID}/approve" >/dev/null || true
  curl -sS -X POST "${BASE_URL}/api/dispatch-requests/${DISPATCH_ID}/execute" || true
  echo
fi

if [[ -n "${TASK_ID}" ]]; then
  echo "Sending result callback for task ${TASK_ID}..."
  curl -sS -X POST "${BASE_URL}/internal/control-plane/tasks/${TASK_ID}/result" \
    -H 'Content-Type: application/json' \
    -d "{\"callbackId\":\"p10-result-${TASK_ID}\",\"taskId\":\"${TASK_ID}\",\"agentId\":\"${AGENT_ID}\",\"success\":true,\"result\":{\"summary\":\"mock completed\"}}"
  echo
fi

echo "Executing pending adapter actions..."
curl -sS -X POST "${BASE_URL}/api/adapter-actions/execute-pending?limit=50"
echo

echo "Recent adapter actions:"
curl -sS "${BASE_URL}/api/adapter-actions?limit=20"
echo

echo "Executor audit:"
curl -sS "${BASE_URL}/api/adapter-actions/executor-audit?limit=20"
echo
