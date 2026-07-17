#!/usr/bin/env bash
set -euo pipefail

CORE_BASE_URL="${CORE_BASE_URL:-${I6_CORE_BASE_URL:-http://127.0.0.1:18080}}"
TASK_ID="${1:-${TASK_ID:-}}"
CALLBACK_KIND="${2:-${CALLBACK_KIND:-result}}"
AGENT_ID="${AGENT_ID:-${I6_AGENT_ID:-agent-worker-001}}"
DISPATCH_REQUEST_ID="${DISPATCH_REQUEST_ID:-}"
ASSIGNMENT_ID="${ASSIGNMENT_ID:-}"
DISPATCH_TOKEN="${DISPATCH_TOKEN:-}"
ATTEMPT_NO="${ATTEMPT_NO:-1}"
OWNER_GATEWAY_NODE_ID="${OWNER_GATEWAY_NODE_ID:-${GATEWAY_NODE_ID:-gateway-node-001}}"
AGENT_SESSION_ID="${AGENT_SESSION_ID:-local-callback-simulator}"
CLUSTER_INTERNAL_TOKEN="${CLUSTER_INTERNAL_TOKEN:-${CORE_INTERNAL_TOKEN:-local-cluster-token-change-me}}"
CORE_INTERNAL_TOKEN_HEADER="${CORE_INTERNAL_TOKEN_HEADER:-X-Cluster-Token}"
ALLOW_SYNTHETIC_CONTEXT="${SIMULATE_ALLOW_SYNTHETIC_CONTEXT:-false}"

usage() {
  cat <<USAGE
Usage: $0 <task-id> [ack|progress|result|error]

Required for strict Core callback validation:
  DISPATCH_REQUEST_ID=<real dispatchRequestId>
  ASSIGNMENT_ID=<real assignmentId>
  DISPATCH_TOKEN=<real dispatchToken>
  ATTEMPT_NO=<attempt number>

This script posts directly to Core internal callback endpoints. It is useful for
isolating Core task lifecycle behavior, but it does not prove Netty relay or TCP
Agent transport unless used with real dispatch context.
USAGE
}

if [[ -z "${TASK_ID}" ]]; then
  usage >&2
  exit 2
fi

if [[ -z "${DISPATCH_REQUEST_ID}" || -z "${ASSIGNMENT_ID}" || -z "${DISPATCH_TOKEN}" ]]; then
  if [[ "${ALLOW_SYNTHETIC_CONTEXT}" != "true" && "${ALLOW_SYNTHETIC_CONTEXT}" != "1" ]]; then
    echo "ERROR: DISPATCH_REQUEST_ID, ASSIGNMENT_ID and DISPATCH_TOKEN are required for strict callback validation." >&2
    echo "Set SIMULATE_ALLOW_SYNTHETIC_CONTEXT=true only for relaxed local Core profiles." >&2
    exit 2
  fi
  DISPATCH_REQUEST_ID="${DISPATCH_REQUEST_ID:-dispatch-${TASK_ID}}"
  ASSIGNMENT_ID="${ASSIGNMENT_ID:-assign-${TASK_ID}}"
  DISPATCH_TOKEN="${DISPATCH_TOKEN:-local-synthetic-dispatch-token-${TASK_ID}}"
fi

case "${CALLBACK_KIND}" in
  ack|progress|result|error) ;;
  *) echo "ERROR: unsupported callback kind: ${CALLBACK_KIND}" >&2; usage >&2; exit 2 ;;
esac

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required." >&2
  exit 1
fi

BASE_PAYLOAD=$(cat <<JSON
{
  "callbackId": "manual-${CALLBACK_KIND}-${TASK_ID}-$(date +%s)",
  "dispatchRequestId": "${DISPATCH_REQUEST_ID}",
  "assignmentId": "${ASSIGNMENT_ID}",
  "taskId": "${TASK_ID}",
  "agentId": "${AGENT_ID}",
  "ownerGatewayNodeId": "${OWNER_GATEWAY_NODE_ID}",
  "agentSessionId": "${AGENT_SESSION_ID}",
  "attemptNo": ${ATTEMPT_NO},
  "dispatchToken": "${DISPATCH_TOKEN}",
  "message": "Manual Phase 3G-P1 callback simulation: ${CALLBACK_KIND}",
  "resultStatus": "SUCCESS",
  "payload": {
    "simulated": true,
    "source": "scripts/agents/simulate-agent-result-callback.sh",
    "summary": "Manual callback simulator completed ${CALLBACK_KIND} for ${TASK_ID}"
  }
}
JSON
)

if [[ "${CALLBACK_KIND}" == "error" ]]; then
  BASE_PAYLOAD=$(python3 - <<PY
import json
payload=json.loads('''${BASE_PAYLOAD}''')
payload['resultStatus']='FAILED'
payload['errorCode']='MANUAL_SIMULATED_ERROR'
payload['errorMessage']='Manual callback simulator requested error callback'
print(json.dumps(payload, ensure_ascii=False))
PY
)
fi

URL="${CORE_BASE_URL%/}/internal/control-plane/tasks/${TASK_ID}/${CALLBACK_KIND}"
echo "POST ${URL}"
curl -sS -X POST "${URL}" \
  -H 'Content-Type: application/json' \
  -H "${CORE_INTERNAL_TOKEN_HEADER}: ${CLUSTER_INTERNAL_TOKEN}" \
  -d "${BASE_PAYLOAD}"
echo
