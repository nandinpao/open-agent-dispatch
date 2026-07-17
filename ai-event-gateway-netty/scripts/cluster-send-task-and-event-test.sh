#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUTO_START_AGENTS="${AUTO_START_AGENTS:-true}"
AUTO_STOP_AGENTS="${AUTO_STOP_AGENTS:-false}"
AGENTS_PER_NODE="${AGENTS_PER_NODE:-1}"
GATEWAY_CLUSTER_NODE_COUNT="${GATEWAY_CLUSTER_NODE_COUNT:-3}"
NETTY_ADMIN_BASE_URL="${NETTY_ADMIN_BASE_URL:-http://localhost:18081}"

export AGENTS_PER_NODE GATEWAY_CLUSTER_NODE_COUNT
export AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN:-local-agent-onboarding-token-change-me}"
export CLUSTER_INTERNAL_TOKEN="${CLUSTER_INTERNAL_TOKEN:-local-cluster-token-change-me}"

cleanup() {
  if [[ "${AUTO_STOP_AGENTS}" == "true" ]]; then
    "${ROOT_DIR}/scripts/cluster-run-many-agents.sh" stop || true
  fi
}
trap cleanup EXIT

if [[ "${AUTO_START_AGENTS}" == "true" ]]; then
  "${ROOT_DIR}/scripts/cluster-run-many-agents.sh" start
  sleep "${AGENT_START_WAIT_SECONDS:-2}"
else
  echo "AUTO_START_AGENTS=false; using existing simulated agents."
fi

echo "--- agent status ---"
"${ROOT_DIR}/scripts/cluster-run-many-agents.sh" status || true

echo "--- send ai.task.requested to node-001 ---"
"${ROOT_DIR}/scripts/cluster-send-event.sh" task-requested "${TASK_ID:-task-demo-001}" 1

echo "--- send heartbeat event to node-001 ---"
"${ROOT_DIR}/scripts/cluster-send-event.sh" agent-heartbeat "${HEARTBEAT_AGENT_ID:-agent-event-node-001}" 1

echo "--- send task command to all simulated agents ---"
NETTY_ADMIN_BASE_URL="${NETTY_ADMIN_BASE_URL}" "${ROOT_DIR}/scripts/cluster-send-task.sh" all

echo "--- runtime probes ---"
if command -v curl >/dev/null 2>&1; then
  curl -sS "${NETTY_ADMIN_BASE_URL}/api/admin/runtime/snapshot" || true
  echo
  curl -sS "${NETTY_ADMIN_BASE_URL}/api/admin/runtime/delivery" || true
  echo
fi
