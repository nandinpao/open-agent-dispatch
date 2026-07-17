#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
THRESHOLD="${1:-3}"
AGENT_ID="${2:-agent-tnn-001}"
GATEWAY_ID="${3:-gateway-tnn-001}"
SESSION_ID="${4:-ws-session-001}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

"${SCRIPT_DIR}/gateway-agent-directory-smoke.sh" "${GATEWAY_ID}" "${AGENT_ID}" "${SESSION_ID}" >/dev/null
for i in $(seq 1 "${THRESHOLD}"); do
  RESPONSE=$(curl -sS -X POST "${BASE_URL}/api/events/intake" \
    -H 'Content-Type: application/json' \
    -d '{"tenantId":"tenant-a","sourceSystem":"MES","siteId":"TNN","plantId":"TNN-FAB-01","objectType":"EQUIPMENT","objectId":"EQP-1001","eventType":"EQUIPMENT_ALARM","errorCode":"TEMP_HIGH","severity":"CRITICAL","message":"Chamber temperature over threshold"}')
  echo "${RESPONSE}"
done

DISPATCH_REQUEST_ID=$(curl -sS "${BASE_URL}/api/dispatch-requests/status/APPROVED?limit=1" | python3 -c 'import json,sys; envelope=json.load(sys.stdin); data=envelope.get("data", envelope) if isinstance(envelope, dict) else envelope; print(data[0]["dispatchRequestId"] if data else "")')
if [ -z "${DISPATCH_REQUEST_ID}" ]; then
  DISPATCH_REQUEST_ID=$(curl -sS "${BASE_URL}/api/dispatch-requests/status/DISPATCHED?limit=1" | python3 -c 'import json,sys; envelope=json.load(sys.stdin); data=envelope.get("data", envelope) if isinstance(envelope, dict) else envelope; print(data[0]["dispatchRequestId"] if data else "")')
fi
if [ -z "${DISPATCH_REQUEST_ID}" ]; then
  echo "No APPROVED/DISPATCHED dispatch request was created." >&2
  exit 1
fi

DISPATCH_JSON=$(curl -sS "${BASE_URL}/api/dispatch-requests/${DISPATCH_REQUEST_ID}")
export DISPATCH_JSON
read -r TASK_ID DISPATCH_TOKEN OWNER_GATEWAY_NODE_ID AGENT_SESSION_ID ATTEMPT_NO AGENT_ID_FROM_DISPATCH <<EOF2
$(python3 - <<'PY'
import json, os
envelope=json.loads(os.environ['DISPATCH_JSON'])
r=envelope.get('data', envelope) if isinstance(envelope, dict) else envelope
print(r.get('taskId',''), r.get('dispatchToken',''), r.get('ownerGatewayNodeId',''), r.get('agentSessionId',''), r.get('attemptCount',1), r.get('agentId',''))
PY
)
EOF2
AGENT_ID="${AGENT_ID_FROM_DISPATCH:-${AGENT_ID}}"

if [ "$(python3 - <<'PY'
import json, os
envelope=json.loads(os.environ['DISPATCH_JSON'])
r=envelope.get('data', envelope) if isinstance(envelope, dict) else envelope
print(r.get('status'))
PY
)" = "APPROVED" ]; then
  "${ROOT_DIR}/scripts/api/dispatch/dispatch-request-execute.sh" "${DISPATCH_REQUEST_ID}"
  DISPATCH_JSON=$(curl -sS "${BASE_URL}/api/dispatch-requests/${DISPATCH_REQUEST_ID}")
  export DISPATCH_JSON
  ATTEMPT_NO=$(python3 - <<'PY'
import json, os
envelope=json.loads(os.environ['DISPATCH_JSON'])
r=envelope.get('data', envelope) if isinstance(envelope, dict) else envelope
print(r.get('attemptCount',1))
PY
)
fi

echo "dispatchRequestId=${DISPATCH_REQUEST_ID} taskId=${TASK_ID} attemptNo=${ATTEMPT_NO} gateway=${OWNER_GATEWAY_NODE_ID} session=${AGENT_SESSION_ID}"
"${ROOT_DIR}/scripts/api/task/task-callback-ack.sh" "${TASK_ID}" "${DISPATCH_REQUEST_ID}" "${AGENT_ID}" "${DISPATCH_TOKEN}" "${OWNER_GATEWAY_NODE_ID}" "${AGENT_SESSION_ID}" "${ATTEMPT_NO}"
"${ROOT_DIR}/scripts/api/task/task-callback-progress.sh" "${TASK_ID}" "${DISPATCH_REQUEST_ID}" 50 "${AGENT_ID}" "${DISPATCH_TOKEN}" "${OWNER_GATEWAY_NODE_ID}" "${AGENT_SESSION_ID}" "${ATTEMPT_NO}"
"${ROOT_DIR}/scripts/api/task/task-callback-result.sh" "${TASK_ID}" "${DISPATCH_REQUEST_ID}" "${AGENT_ID}" "${DISPATCH_TOKEN}" "${OWNER_GATEWAY_NODE_ID}" "${AGENT_SESSION_ID}" "${ATTEMPT_NO}"
curl -sS "${BASE_URL}/api/tasks/${TASK_ID}"; echo
"${ROOT_DIR}/scripts/api/dispatch/dispatch-request-query.sh" 10
