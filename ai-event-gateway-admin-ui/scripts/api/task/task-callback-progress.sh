#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
TASK_ID="${1:-}"
DISPATCH_REQUEST_ID="${2:-}"
PROGRESS="${3:-50}"
AGENT_ID="${4:-agent-tnn-001}"
DISPATCH_TOKEN="${5:-${DISPATCH_TOKEN:-}}"
OWNER_GATEWAY_NODE_ID="${6:-${OWNER_GATEWAY_NODE_ID:-gateway-tnn-001}}"
AGENT_SESSION_ID="${7:-${AGENT_SESSION_ID:-ws-session-001}}"
ATTEMPT_NO="${8:-${ATTEMPT_NO:-1}}"
if [ -z "${TASK_ID}" ]; then
  echo "Usage: $0 <taskId> [dispatchRequestId] [progressPercent] [agentId] [dispatchToken] [ownerGatewayNodeId] [agentSessionId] [attemptNo]" >&2
  exit 1
fi
curl -sS -X POST "${BASE_URL}/internal/control-plane/tasks/${TASK_ID}/progress" \
  -H 'Content-Type: application/json' \
  -d "{\"dispatchRequestId\":\"${DISPATCH_REQUEST_ID}\",\"agentId\":\"${AGENT_ID}\",\"ownerGatewayNodeId\":\"${OWNER_GATEWAY_NODE_ID}\",\"agentSessionId\":\"${AGENT_SESSION_ID}\",\"attemptNo\":${ATTEMPT_NO},\"dispatchToken\":\"${DISPATCH_TOKEN}\",\"progressPercent\":${PROGRESS},\"message\":\"Agent progress ${PROGRESS}%\"}"
echo
