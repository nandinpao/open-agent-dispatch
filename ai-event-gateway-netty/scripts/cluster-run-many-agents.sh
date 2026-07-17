#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="$(cd "${ROOT_DIR}/.." && pwd)"
RUNTIME_DIR="${AGENT_RUNTIME_DIR:-${ROOT_DIR}/.runtime/agents}"
LOG_ROOT="${OPENDISPATCH_LOG_ROOT:-${PROJECT_ROOT}/.local/opendispatch-logs}"
LOG_DIR="${AGENT_LOG_DIR:-${OPENDISPATCH_AGENT_LOG_DIR:-${LOG_ROOT}/agents}}"
PENDING_DIR="${AGENT_PENDING_CALLBACK_DIR:-${OPENDISPATCH_AGENT_PENDING_CALLBACK_DIR:-${LOG_DIR}/pending-callbacks}}"
CLIENT="${ROOT_DIR}/scripts/netty-tcp-agent-client.js"
BOOTSTRAP_CLIENT="${ROOT_DIR}/scripts/core-bootstrap-cluster-agents.js"
CMD="${1:-status}"
CMD_ARG1="${2:-}"

NODE_COUNT="${GATEWAY_CLUSTER_NODE_COUNT:-3}"
AGENTS_PER_NODE="${AGENTS_PER_NODE:-1}"
BASE_TCP_PORT="${GATEWAY_TCP_BASE_PORT:-19090}"
TCP_PORT_STEP="${GATEWAY_TCP_PORT_STEP:-2}"
GATEWAY_HOST="${GATEWAY_HOST:-127.0.0.1}"
AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN:-local-agent-onboarding-token-change-me}"
AGENT_HEARTBEAT_INTERVAL_MS="${AGENT_HEARTBEAT_INTERVAL_MS:-5000}"
AGENT_MAX_CONCURRENT_TASKS="${AGENT_MAX_CONCURRENT_TASKS:-3}"
AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"
AGENT_WORKER_PROCESSING_MS="${AGENT_WORKER_PROCESSING_MS:-${AGENT_WORKER_RESULT_DELAY_MS:-8000}}"
AGENT_CALLBACK_REPLAY_ENABLED="${AGENT_CALLBACK_REPLAY_ENABLED:-true}"
AGENT_CALLBACK_REPLAY_ON_CONNECT="${AGENT_CALLBACK_REPLAY_ON_CONNECT:-true}"
AGENT_CALLBACK_REPLAY_INTERVAL_MS="${AGENT_CALLBACK_REPLAY_INTERVAL_MS:-15000}"
AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS="${AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS:-0}"
# Runtime-reported capabilities are optional diagnostics only. Bootstrap never
# creates Assignment Profiles, Qualifications, Service Scopes or Capability presets.
AGENT_LEGACY_CAPABILITIES_ENABLED="${AGENT_LEGACY_CAPABILITIES_ENABLED:-false}"
OPENSOCKET_AGENT_CAPABILITIES="${OPENSOCKET_AGENT_CAPABILITIES:-${AGENT_CAPABILITIES:-}}"
AGENT_CAPABILITIES="${AGENT_CAPABILITIES:-${OPENSOCKET_AGENT_CAPABILITIES}}"
CORE_BOOTSTRAP_AGENTS="${CORE_BOOTSTRAP_AGENTS:-false}"
DEFAULT_BOOTSTRAP_ENV_FILE="${ENV_FILE:-deploy/env/.env.local}"
if [[ ! -f "${DEFAULT_BOOTSTRAP_ENV_FILE}" ]]; then
  DEFAULT_BOOTSTRAP_ENV_FILE="deploy/env/.env.local.example"
fi
CORE_BOOTSTRAP_ENV_FILE="${CORE_BOOTSTRAP_ENV_FILE:-${DEFAULT_BOOTSTRAP_ENV_FILE}}"
CORE_BOOTSTRAP_ADMIN_AUTH_MODE="${CORE_BOOTSTRAP_ADMIN_AUTH_MODE:-SESSION}"

mkdir -p "${LOG_DIR}" "${PENDING_DIR}"

node_id() {
  printf 'gateway-node-%03d' "$1"
}

agent_id() {
  printf 'agent-cluster-node-%03d-%03d' "$1" "$2"
}

tcp_port() {
  local idx="$1"
  echo $((BASE_TCP_PORT + (idx - 1) * TCP_PORT_STEP))
}

pid_file() {
  echo "${RUNTIME_DIR}/$1.pid"
}

env_file() {
  echo "${RUNTIME_DIR}/$1.env"
}

normalize_capability_csv() {
  local raw="$1"
  if [[ -z "${raw}" ]]; then
    return 0
  fi
  echo "${raw}" | tr ',' '
' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | awk 'NF { gsub(/[ .-]+/, "_"); print toupper($0) }' | paste -sd, -
}

preset_capabilities_for_agent() {
  local agent="$1"
  local node_idx="$2"
  local agent_idx="$3"
  local explicit="${OPENSOCKET_AGENT_CAPABILITIES:-${AGENT_CAPABILITIES:-}}"
  if [[ -n "${explicit}" ]]; then
    # Explicit runtime observations are allowed for diagnostics, but dispatch must not depend on them.
    normalize_capability_csv "${explicit}"
    return 0
  fi
  # Do not auto-populate business capabilities into runtime heartbeats. Core bootstrap/Admin UI
  # owns capability approval and service-scope assignment.
  return 0
}

is_running() {
  local pid="$1"
  [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null
}

bootstrap_core_agents() {
  if ! command -v node >/dev/null 2>&1; then
    echo "ERROR: node is required for the Core agent bootstrap helper." >&2
    exit 1
  fi
  AGENTS_PER_NODE="${AGENTS_PER_NODE}" \
  GATEWAY_CLUSTER_NODE_COUNT="${NODE_COUNT}" \
  AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN}" \
  AGENT_MAX_CONCURRENT_TASKS="${AGENT_MAX_CONCURRENT_TASKS}" \
  CORE_BOOTSTRAP_ENV_FILE="${CORE_BOOTSTRAP_ENV_FILE}" \
  CORE_BOOTSTRAP_ADMIN_AUTH_MODE="${CORE_BOOTSTRAP_ADMIN_AUTH_MODE}" \
  node "${BOOTSTRAP_CLIENT}"
}

start_agent_process() {
  local agent="$1"
  local node="$2"
  local port="$3"
  local pf="$(pid_file "${agent}")"
  if [[ -f "${pf}" ]] && is_running "$(cat "${pf}")"; then
    echo "RUNNING ${agent} pid=$(cat "${pf}")"
    return 0
  fi
  local runtime_capabilities="$(preset_capabilities_for_agent "${agent}" "${node#gateway-node-}" "${agent##*-}")"
  AGENT_ID="${agent}" \
  GATEWAY_NODE_ID="${node}" \
  GATEWAY_HOST="${GATEWAY_HOST}" \
  GATEWAY_TCP_HOST="${GATEWAY_HOST}" \
  GATEWAY_TCP_PORT="${port}" \
  AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN}" \
  AGENT_HEARTBEAT_INTERVAL_MS="${AGENT_HEARTBEAT_INTERVAL_MS}" \
  AGENT_WORKER_MODE="${AGENT_WORKER_MODE}" \
  AGENT_WORKER_PROCESSING_MS="${AGENT_WORKER_PROCESSING_MS}" \
  AGENT_MAX_CONCURRENT_TASKS="${AGENT_MAX_CONCURRENT_TASKS}" \
  AGENT_LEGACY_CAPABILITIES_ENABLED="${AGENT_LEGACY_CAPABILITIES_ENABLED}" \
  OPENSOCKET_AGENT_CAPABILITIES="${runtime_capabilities}" \
  AGENT_CAPABILITIES="${runtime_capabilities}" \
  AGENT_CALLBACK_REPLAY_ENABLED="${AGENT_CALLBACK_REPLAY_ENABLED}" \
  AGENT_CALLBACK_REPLAY_ON_CONNECT="${AGENT_CALLBACK_REPLAY_ON_CONNECT}" \
  AGENT_CALLBACK_REPLAY_INTERVAL_MS="${AGENT_CALLBACK_REPLAY_INTERVAL_MS}" \
  AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS="${AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS}" \
  AGENT_PENDING_CALLBACK_STORE="${PENDING_DIR}/${agent}.json" \
  nohup node "${CLIENT}" run > "${LOG_DIR}/${agent}.log" 2>&1 &
  local pid=$!
  echo "${pid}" > "${pf}"
  cat > "$(env_file "${agent}")" <<ENVEOF
AGENT_ID=${agent}
GATEWAY_NODE_ID=${node}
GATEWAY_HOST=${GATEWAY_HOST}
GATEWAY_TCP_PORT=${port}
AGENT_WORKER_MODE=${AGENT_WORKER_MODE}
AGENT_WORKER_PROCESSING_MS=${AGENT_WORKER_PROCESSING_MS}
AGENT_MAX_CONCURRENT_TASKS=${AGENT_MAX_CONCURRENT_TASKS}
AGENT_LEGACY_CAPABILITIES_ENABLED=${AGENT_LEGACY_CAPABILITIES_ENABLED}
OPENSOCKET_AGENT_CAPABILITIES=${runtime_capabilities}
AGENT_CAPABILITIES=${runtime_capabilities}
AGENT_CALLBACK_REPLAY_ENABLED=${AGENT_CALLBACK_REPLAY_ENABLED}
AGENT_CALLBACK_REPLAY_ON_CONNECT=${AGENT_CALLBACK_REPLAY_ON_CONNECT}
AGENT_CALLBACK_REPLAY_INTERVAL_MS=${AGENT_CALLBACK_REPLAY_INTERVAL_MS}
AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS=${AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS}
AGENT_PENDING_CALLBACK_STORE=${PENDING_DIR}/${agent}.json
PID=${pid}
LOG=${LOG_DIR}/${agent}.log
ENVEOF
  echo "STARTED ${agent} node=${node} tcp=${GATEWAY_HOST}:${port} worker=${AGENT_WORKER_MODE} processingMs=${AGENT_WORKER_PROCESSING_MS} maxConcurrentTasks=${AGENT_MAX_CONCURRENT_TASKS} replay=${AGENT_CALLBACK_REPLAY_ENABLED}/${AGENT_CALLBACK_REPLAY_ON_CONNECT} intervalMs=${AGENT_CALLBACK_REPLAY_INTERVAL_MS} maxAttempts=${AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS} pid=${pid}"
}

stop_agent_by_id() {
  local agent="$1"
  local pf="$(pid_file "${agent}")"
  local pid=""
  [[ -f "${pf}" ]] && pid="$(cat "${pf}" 2>/dev/null || true)"
  if is_running "${pid}"; then
    kill "${pid}" 2>/dev/null || true
    echo "STOPPED ${agent} pid=${pid}"
  fi
  rm -f "${pf}" "$(env_file "${agent}")"
}

stop_node_agents() {
  local requested="${CMD_ARG1:-${TARGET_NODE_INDEX:-}}"
  if [[ -z "${requested}" ]]; then
    echo "ERROR: stop-node requires a node index, for example: $0 stop-node 2" >&2
    exit 2
  fi
  local node="$(node_id "${requested}")"
  local stopped=0
  if compgen -G "${RUNTIME_DIR}/*.env" >/dev/null; then
    for ef in "${RUNTIME_DIR}"/*.env; do
      local agent="$(basename "${ef}" .env)"
      local current_node="$(sed -n 's/^GATEWAY_NODE_ID=//p' "${ef}" | head -1)"
      if [[ "${current_node}" == "${node}" ]]; then
        stop_agent_by_id "${agent}"
        stopped=$((stopped + 1))
      fi
    done
  fi
  echo "Stopped ${stopped} simulated agent(s) for ${node}."
}

reconnect_agent_to_node() {
  local agent="${AGENT_ID:-${CMD_ARG1:-}}"
  if [[ -z "${agent}" ]]; then
    echo "ERROR: reconnect-agent-to-node requires AGENT_ID or positional agent id." >&2
    exit 2
  fi
  local node_idx="${TARGET_NODE_INDEX:-1}"
  local node="$(node_id "${node_idx}")"
  local port="$(tcp_port "${node_idx}")"
  stop_agent_by_id "${agent}"
  AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"
  start_agent_process "${agent}" "${node}" "${port}"
  echo "Reconnected ${agent} to ${node}; pending callback store remains ${PENDING_DIR}/${agent}.json."
}

start_agents() {
  if ! command -v node >/dev/null 2>&1; then
    echo "ERROR: node is required for the TCP agent simulator." >&2
    exit 1
  fi
  if [[ "${CORE_BOOTSTRAP_AGENTS}" == "true" || "${CORE_BOOTSTRAP_AGENTS}" == "1" ]]; then
    bootstrap_core_agents
  fi
  for node_idx in $(seq 1 "${NODE_COUNT}"); do
    local node="$(node_id "${node_idx}")"
    local port="$(tcp_port "${node_idx}")"
    for agent_idx in $(seq 1 "${AGENTS_PER_NODE}"); do
      local agent="$(agent_id "${node_idx}" "${agent_idx}")"
      start_agent_process "${agent}" "${node}" "${port}"
    done
  done
}

status_agents() {
  if ! compgen -G "${RUNTIME_DIR}/*.pid" >/dev/null; then
    echo "No simulated agents found under ${RUNTIME_DIR}."
    return 0
  fi
  for pf in "${RUNTIME_DIR}"/*.pid; do
    local agent="$(basename "${pf}" .pid)"
    local pid="$(cat "${pf}" 2>/dev/null || true)"
    if is_running "${pid}"; then
      local worker="unknown"
      [[ -f "$(env_file "${agent}")" ]] && worker="$(sed -n 's/^AGENT_WORKER_MODE=//p' "$(env_file "${agent}")" | head -1)"
      local store="${PENDING_DIR}/${agent}.json"
      local pending="0"
      [[ -s "${store}" ]] && pending="stored"
      local processing="unknown"
      local max_tasks="unknown"
      [[ -f "$(env_file "${agent}")" ]] && processing="$(sed -n 's/^AGENT_WORKER_PROCESSING_MS=//p' "$(env_file "${agent}")" | head -1)"
      [[ -f "$(env_file "${agent}")" ]] && max_tasks="$(sed -n 's/^AGENT_MAX_CONCURRENT_TASKS=//p' "$(env_file "${agent}")" | head -1)"
      echo "ONLINE  ${agent} worker=${worker:-unknown} processingMs=${processing:-unknown} maxConcurrentTasks=${max_tasks:-unknown} pendingCallbacks=${pending} pid=${pid} log=${LOG_DIR}/${agent}.log"
    else
      echo "OFFLINE ${agent} pid=${pid} log=${LOG_DIR}/${agent}.log"
    fi
  done
}

stop_agents() {
  if ! compgen -G "${RUNTIME_DIR}/*.pid" >/dev/null; then
    echo "No simulated agents to stop."
    return 0
  fi
  for pf in "${RUNTIME_DIR}"/*.pid; do
    local agent="$(basename "${pf}" .pid)"
    local pid="$(cat "${pf}" 2>/dev/null || true)"
    if is_running "${pid}"; then
      kill "${pid}" 2>/dev/null || true
      echo "STOPPED ${agent} pid=${pid}"
    fi
    rm -f "${pf}" "$(env_file "${agent}")"
  done
}


doctor() {
  echo "OpenDispatch simulated Agent doctor"
  echo "- command: ${CMD}"
  echo "- gateway: ${GATEWAY_HOST}:${BASE_TCP_PORT} nodeCount=${NODE_COUNT} agentsPerNode=${AGENTS_PER_NODE}"
  echo "- logs: ${LOG_DIR}"
  echo "- runtime: ${RUNTIME_DIR}"
  echo "- pendingCallbacks: ${PENDING_DIR}"
  echo "- workerMode=${AGENT_WORKER_MODE} processingMs=${AGENT_WORKER_PROCESSING_MS} maxConcurrentTasks=${AGENT_MAX_CONCURRENT_TASKS:-3}"
  echo "- runtimeCapabilities=${OPENSOCKET_AGENT_CAPABILITIES:-${AGENT_CAPABILITIES:-NONE}} legacyRuntimeFlag=${AGENT_LEGACY_CAPABILITIES_ENABLED}"
  echo "- coreBootstrap=${CORE_BOOTSTRAP_AGENTS} coreBase=${CORE_BASE_URL:-${CORE_API_BASE_URL:-http://127.0.0.1:18080}} adminAuth=${CORE_BOOTSTRAP_ADMIN_AUTH_MODE} envFile=${CORE_BOOTSTRAP_ENV_FILE}"
  echo "- legacyBootstrap=disabled (no Assignment Profile, Qualification, Service Scope or Capability preset writes)"
  echo ""
  echo "Expected local/SIT flow:"
  echo "1) CORE_BOOTSTRAP_AGENTS=true creates/syncs Agent identity, approval, credential and runtime binding only."
  echo "   Dispatch Flow selects Agents; Capabilities are optional and are managed explicitly in Core/Admin UI."
  echo "   Optional diagnostics: set OPENSOCKET_AGENT_CAPABILITIES=custom,csv for runtime observations only."
  echo "2) restart starts worker agents. Default workerMode=process-result, not observe."
  echo "3) Worker logs must show: worker received task -> TASK_ACK -> TASK_PROGRESS 25/50/75 -> TASK_RESULT."
  echo "4) Use AGENT_WORKER_MODE=work-only to keep tasks in progress without sending RESULT."
  echo "5) For capability readiness, approve capabilities in Admin UI/Core. Runtime only needs identity, credential, heartbeat and capacity."
}

case "${CMD}" in
  bootstrap-core) bootstrap_core_agents ;;
  start) start_agents ;;
  start-worker) AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"; start_agents ;;
  start-single-worker) NODE_COUNT=1; AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"; start_agents ;;
  start-cluster-worker) NODE_COUNT="${TOPOLOGY_CLUSTER_NODE_COUNT:-3}"; AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"; start_agents ;;
  start-observer) AGENT_WORKER_MODE="observe"; start_agents ;;
  status) status_agents ;;
  stop) stop_agents ;;
  restart) stop_agents; start_agents ;;
  restart-worker) stop_agents; AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"; start_agents ;;
  restart-single-worker) stop_agents; NODE_COUNT=1; AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"; start_agents ;;
  restart-cluster-worker) stop_agents; NODE_COUNT="${TOPOLOGY_CLUSTER_NODE_COUNT:-3}"; AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"; start_agents ;;
  restart-observer) stop_agents; AGENT_WORKER_MODE="observe"; start_agents ;;
  stop-node) stop_node_agents ;;
  reconnect-agent-to-node) reconnect_agent_to_node ;;
  doctor) doctor ;;
  clear-pending)
    rm -f "${PENDING_DIR}"/*.json 2>/dev/null || true
    echo "Cleared pending callback stores under ${PENDING_DIR}."
    ;;
  logs)
    log_files=()
    if compgen -G "${RUNTIME_DIR}/*.pid" >/dev/null; then
      for pf in "${RUNTIME_DIR}"/*.pid; do
        agent="$(basename "${pf}" .pid)"
        lf="${LOG_DIR}/${agent}.log"
        [[ -f "${lf}" ]] && log_files+=("${lf}")
      done
    fi
    if [[ "${#log_files[@]}" -eq 0 ]]; then
      if compgen -G "${LOG_DIR}/*.log" >/dev/null; then
        while IFS= read -r lf; do log_files+=("${lf}"); done < <(find "${LOG_DIR}" -maxdepth 1 -name '*.log' -type f | sort)
      else
        echo "No simulated agent logs found under ${LOG_DIR}."
        exit 0
      fi
    fi
    tail -n "${TAIL_LINES:-80}" -f "${log_files[@]}"
    ;;
  *)
    echo "Usage: $0 {bootstrap-core|doctor|start|start-worker|start-single-worker|start-cluster-worker|start-observer|status|stop|stop-node|restart|restart-worker|restart-single-worker|restart-cluster-worker|restart-observer|reconnect-agent-to-node|clear-pending|logs}" >&2
    exit 2
    ;;
esac
