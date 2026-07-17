#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/agents"
TARGET="${1:-all}"
NETTY_ADMIN_BASE_URL="${NETTY_ADMIN_BASE_URL:-${GATEWAY_NODE_001_BASE_URL:-http://localhost:18081}}"
CLUSTER_INTERNAL_TOKEN="${CLUSTER_INTERNAL_TOKEN:-local-cluster-token-change-me}"
TASK_ID_PREFIX="${TASK_ID_PREFIX:-task-command}"
TIMEOUT_MS="${TASK_DELIVERY_TIMEOUT_MS:-5000}"
ENDPOINT_KIND="${TASK_DELIVERY_ENDPOINT_KIND:-internal-cluster}"

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required." >&2
  exit 1
fi

collect_agents() {
  if [[ "${TARGET}" != "all" ]]; then
    echo "${TARGET}"
    return 0
  fi
  if compgen -G "${RUNTIME_DIR}/*.env" >/dev/null; then
    for file in "${RUNTIME_DIR}"/*.env; do
      sed -n 's/^AGENT_ID=//p' "${file}"
    done | sort -u
    return 0
  fi
  echo "ERROR: TARGET=all but no agent env files found. Start agents first or specify an agent id." >&2
  exit 2
}

api_prefix() {
  case "${ENDPOINT_KIND}" in
    internal-cluster) echo "/internal/cluster/delivery" ;;
    api-cluster) echo "/api/cluster/delivery" ;;
    local) echo "/internal/delivery" ;;
    *)
      echo "ERROR: Unsupported TASK_DELIVERY_ENDPOINT_KIND=${ENDPOINT_KIND}" >&2
      exit 2
      ;;
  esac
}

send_one() {
  local agent_id="$1"
  local task_id="${TASK_ID_PREFIX}-${agent_id}-$(date +%s)"
  local command_id="cmd-${task_id}"
  local url="${NETTY_ADMIN_BASE_URL}$(api_prefix)/agents/${agent_id}/commands"
  local body
  body=$(cat <<JSON
{
  "commandId": "${command_id}",
  "messageType": "TASK_DISPATCH",
  "traceId": "trace-${task_id}",
  "issuedBy": "cluster-send-task.sh",
  "timeoutMs": ${TIMEOUT_MS},
  "payload": {
    "taskId": "${task_id}",
    "agentId": "${agent_id}",
    "assignmentId": "assign-${task_id}",
    "dispatchRequestId": "dispatch-${task_id}",
    "dispatchToken": "local-demo-dispatch-token-${task_id}",
    "attemptNo": 1,
    "taskType": "demo.command",
    "priority": 1,
    "input": {
      "message": "Hello from cluster-send-task.sh",
      "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    }
  }
}
JSON
)
  echo "POST ${url}"
  curl -sS -X POST "${url}" \
    -H 'Content-Type: application/json' \
    -H "X-Cluster-Token: ${CLUSTER_INTERNAL_TOKEN}" \
    -d "${body}"
  echo
}

mapfile -t AGENTS < <(collect_agents)
if [[ "${#AGENTS[@]}" -eq 0 ]]; then
  echo "No target agents." >&2
  exit 2
fi
for agent in "${AGENTS[@]}"; do
  send_one "${agent}"
done
