#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AGENTS="${ROOT_DIR}/scripts/cluster-run-many-agents.sh"
SCENARIO="${1:-help}"
EXECUTE="${EXECUTE:-0}"
WAIT_SECONDS="${WAIT_SECONDS:-8}"
TASK_ID="${TASK_ID:-}"
CORE_ADMIN_BASE_URL="${CORE_ADMIN_BASE_URL:-http://127.0.0.1:18080}"
FAILOVER_AGENT_ID="${FAILOVER_AGENT_ID:-agent-cluster-node-002-001}"
FAILOVER_FROM_NODE="${FAILOVER_FROM_NODE:-2}"
FAILOVER_TO_NODE="${FAILOVER_TO_NODE:-1}"
LEGACY_CAPABILITIES_ENABLED="${AGENT_LEGACY_CAPABILITIES_ENABLED:-false}"
LEGACY_CAPABILITIES="${AGENT_CAPABILITIES:-}"

print_cmd() {
  printf '+ %s\n' "$*"
}

run_cmd() {
  print_cmd "$*"
  if [[ "${EXECUTE}" == "1" || "${EXECUTE}" == "true" ]]; then
    eval "$@"
  fi
}

common_env() {
  cat <<ENVEOF
CORE_BOOTSTRAP_AGENTS=true
AGENT_WORKER_MODE=process-result
AGENT_WORKER_PROCESSING_MS=${AGENT_WORKER_PROCESSING_MS:-5000}
AGENT_CALLBACK_REPLAY_ENABLED=true
AGENT_CALLBACK_REPLAY_ON_CONNECT=true
AGENT_LEGACY_CAPABILITIES_ENABLED=${LEGACY_CAPABILITIES_ENABLED}
AGENT_CAPABILITIES=${LEGACY_CAPABILITIES:-NONE}
ENVEOF
}

acceptance_hint() {
  echo "==> Acceptance check"
  if [[ -n "${TASK_ID}" ]]; then
    echo "    CORE_ADMIN_BASE_URL=${CORE_ADMIN_BASE_URL} TASK_ID=${TASK_ID} ./scripts/agents/assert-dynamic-topology-recovery.sh"
    run_cmd "CORE_ADMIN_BASE_URL='${CORE_ADMIN_BASE_URL}' TASK_ID='${TASK_ID}' '${ROOT_DIR}/scripts/agents/assert-dynamic-topology-recovery.sh'"
  else
    echo "    Set TASK_ID=<created-task-id> and run:"
    echo "    CORE_ADMIN_BASE_URL=${CORE_ADMIN_BASE_URL} TASK_ID=<created-task-id> ./scripts/agents/assert-dynamic-topology-recovery.sh"
  fi
}

usage() {
  cat <<USAGE
Usage: $0 {single-to-cluster|cluster-node-failover|cluster-to-single|help}

This script orchestrates topology-recovery test steps for the simulated agents.
It does not create a business Task by itself; create one from Admin UI Dispatch Recipe
or the event intake API between the printed steps.

Default mode is dry-run. Set EXECUTE=1 to run agent topology commands.

Scenarios:
  single-to-cluster       Start one worker, let a task begin, restart as cluster workers, replay pending callbacks.
  cluster-node-failover   Start three workers, stop one node, reconnect its agent to another node and replay.
  cluster-to-single       Start three workers, stop all, reconnect a selected cluster agent to single node and replay.

Important assertions:
  - Task/Callback truth is Core Dispatch Ledger + Callback Inbox.
  - Gateway node telemetry is diagnostics only.
  - Replay identity is dispatchRequestId + taskId + idempotencyKey/callbackId.
  - Acceptance should be checked with scripts/agents/assert-dynamic-topology-recovery.sh.
USAGE
}

single_to_cluster() {
  echo "==> Scenario: single -> cluster callback replay"
  echo "==> Environment"
  common_env
  run_cmd "CORE_BOOTSTRAP_AGENTS=true GATEWAY_CLUSTER_NODE_COUNT=1 AGENTS_PER_NODE=1 AGENT_WORKER_MODE=process-result AGENT_WORKER_PROCESSING_MS=${AGENT_WORKER_PROCESSING_MS:-5000} AGENT_CALLBACK_REPLAY_ENABLED=true AGENT_CALLBACK_REPLAY_ON_CONNECT=true AGENT_LEGACY_CAPABILITIES_ENABLED='${LEGACY_CAPABILITIES_ENABLED}' AGENT_CAPABILITIES='${LEGACY_CAPABILITIES}' '${AGENTS}' restart-single-worker"
  echo "==> Create a Dispatch Recipe test Task now and wait until the agent stores or sends a terminal callback."
  echo "    Suggested UI path: Admin UI -> 派工方案 -> Step 5/6 -> 直接送出測試事件."
  if [[ "${EXECUTE}" == "1" || "${EXECUTE}" == "true" ]]; then sleep "${WAIT_SECONDS}"; fi
  run_cmd "CORE_BOOTSTRAP_AGENTS=true TOPOLOGY_CLUSTER_NODE_COUNT=3 AGENTS_PER_NODE=1 AGENT_WORKER_MODE=process-result AGENT_CALLBACK_REPLAY_ENABLED=true AGENT_CALLBACK_REPLAY_ON_CONNECT=true AGENT_LEGACY_CAPABILITIES_ENABLED='${LEGACY_CAPABILITIES_ENABLED}' AGENT_CAPABILITIES='${LEGACY_CAPABILITIES}' '${AGENTS}' restart-cluster-worker"
  run_cmd "'${AGENTS}' status"
  echo "==> Verify in Task Detail: Dispatch Ledger / Callback Inbox, not Gateway Node Detail, is authoritative."
  acceptance_hint
}

cluster_node_failover() {
  echo "==> Scenario: cluster node failover callback replay"
  echo "==> Environment"
  common_env
  run_cmd "CORE_BOOTSTRAP_AGENTS=true TOPOLOGY_CLUSTER_NODE_COUNT=3 AGENTS_PER_NODE=1 AGENT_WORKER_MODE=process-result AGENT_WORKER_PROCESSING_MS=${AGENT_WORKER_PROCESSING_MS:-5000} AGENT_CALLBACK_REPLAY_ENABLED=true AGENT_CALLBACK_REPLAY_ON_CONNECT=true AGENT_LEGACY_CAPABILITIES_ENABLED='${LEGACY_CAPABILITIES_ENABLED}' AGENT_CAPABILITIES='${LEGACY_CAPABILITIES}' '${AGENTS}' restart-cluster-worker"
  echo "==> Create a test Task assigned to ${FAILOVER_AGENT_ID}, then simulate node ${FAILOVER_FROM_NODE} failure."
  if [[ "${EXECUTE}" == "1" || "${EXECUTE}" == "true" ]]; then sleep "${WAIT_SECONDS}"; fi
  run_cmd "'${AGENTS}' stop-node '${FAILOVER_FROM_NODE}'"
  run_cmd "AGENT_ID='${FAILOVER_AGENT_ID}' TARGET_NODE_INDEX='${FAILOVER_TO_NODE}' AGENT_WORKER_MODE=process-result AGENT_WORKER_PROCESSING_MS=${AGENT_WORKER_PROCESSING_MS:-5000} AGENT_CALLBACK_REPLAY_ENABLED=true AGENT_CALLBACK_REPLAY_ON_CONNECT=true AGENT_LEGACY_CAPABILITIES_ENABLED='${LEGACY_CAPABILITIES_ENABLED}' AGENT_CAPABILITIES='${LEGACY_CAPABILITIES}' '${AGENTS}' reconnect-agent-to-node"
  run_cmd "'${AGENTS}' status"
  echo "==> Verify pending callback replay was accepted by Core Callback Inbox with idempotency de-duplication."
  acceptance_hint
}

cluster_to_single() {
  echo "==> Scenario: cluster -> single callback replay"
  echo "==> Environment"
  common_env
  run_cmd "CORE_BOOTSTRAP_AGENTS=true TOPOLOGY_CLUSTER_NODE_COUNT=3 AGENTS_PER_NODE=1 AGENT_WORKER_MODE=process-result AGENT_WORKER_PROCESSING_MS=${AGENT_WORKER_PROCESSING_MS:-5000} AGENT_CALLBACK_REPLAY_ENABLED=true AGENT_CALLBACK_REPLAY_ON_CONNECT=true AGENT_LEGACY_CAPABILITIES_ENABLED='${LEGACY_CAPABILITIES_ENABLED}' AGENT_CAPABILITIES='${LEGACY_CAPABILITIES}' '${AGENTS}' restart-cluster-worker"
  echo "==> Create a test Task assigned to ${FAILOVER_AGENT_ID}, then collapse to one Gateway endpoint."
  if [[ "${EXECUTE}" == "1" || "${EXECUTE}" == "true" ]]; then sleep "${WAIT_SECONDS}"; fi
  run_cmd "'${AGENTS}' stop"
  run_cmd "AGENT_ID='${FAILOVER_AGENT_ID}' TARGET_NODE_INDEX=1 AGENT_WORKER_MODE=process-result AGENT_WORKER_PROCESSING_MS=${AGENT_WORKER_PROCESSING_MS:-5000} AGENT_CALLBACK_REPLAY_ENABLED=true AGENT_CALLBACK_REPLAY_ON_CONNECT=true AGENT_LEGACY_CAPABILITIES_ENABLED='${LEGACY_CAPABILITIES_ENABLED}' AGENT_CAPABILITIES='${LEGACY_CAPABILITIES}' '${AGENTS}' reconnect-agent-to-node"
  run_cmd "'${AGENTS}' status"
  echo "==> Verify Task completion is recovered by Core Callback Inbox after cluster -> single transition."
  acceptance_hint
}

case "${SCENARIO}" in
  single-to-cluster) single_to_cluster ;;
  cluster-node-failover) cluster_node_failover ;;
  cluster-to-single) cluster_to_single ;;
  help|-h|--help) usage ;;
  *) echo "Unknown scenario: ${SCENARIO}" >&2; usage; exit 2 ;;
esac
