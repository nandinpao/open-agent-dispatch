#!/usr/bin/env bash
set -euo pipefail
CORE_BASE_URL="${CORE_BASE_URL:-${I7_CORE_BASE_URL:-http://127.0.0.1:18080}}"
TASK_ID="${TASK_ID:-}"
TOKEN_HEADER="${CLUSTER_INTERNAL_TOKEN_HEADER:-X-Cluster-Token}"
TOKEN="${CLUSTER_INTERNAL_TOKEN:-}"
CURL=(curl -fsS --max-time "${CURL_TIMEOUT_SECONDS:-10}")

if [ -z "$TASK_ID" ]; then
  echo "TASK_ID is required for callback troubleshooting" >&2
  echo "Example: TASK_ID=<id> CLUSTER_INTERNAL_TOKEN=<token> $0" >&2
  exit 2
fi

echo "[callback] task=${TASK_ID} endpoint probes"
for endpoint in ack progress result error; do
  echo "- /internal/control-plane/tasks/${TASK_ID}/${endpoint}"
  "${CURL[@]}" -o /dev/null -w "HTTP %{http_code}\n" ${TOKEN:+-H "${TOKEN_HEADER}: ${TOKEN}"} \
    -H 'Content-Type: application/json' \
    -X POST "${CORE_BASE_URL}/internal/control-plane/tasks/${TASK_ID}/${endpoint}" \
    -d '{"callbackId":"probe-only","taskId":"'"${TASK_ID}"'","agentId":"probe-agent"}' || true
 done

echo "[callback] diagnostics"
echo "- 401/403: check CORE_INTERNAL_SECURITY_ENABLED and gateway token mapping."
echo "- 409 or rejected transition: check attemptNo, dispatchToken, terminal state, and I3 CAS conditions."
echo "- Netty RELAY_QUEUED but no Core update: check CoreOutboundDispatcher queue and Core logs."
