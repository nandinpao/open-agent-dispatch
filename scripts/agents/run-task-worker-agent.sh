#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MOCK_AGENT="${ROOT_DIR}/ai-event-gateway-core/scripts/e2e/mock_task_agent.py"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ ! -f "${MOCK_AGENT}" ]]; then
  echo "ERROR: missing mock task agent: ${MOCK_AGENT}" >&2
  exit 1
fi
if ! command -v "${PYTHON_BIN}" >/dev/null 2>&1; then
  echo "ERROR: ${PYTHON_BIN} is required." >&2
  exit 1
fi

export I6_GATEWAY_TCP_HOST="${I6_GATEWAY_TCP_HOST:-${GATEWAY_TCP_HOST:-${GATEWAY_HOST:-127.0.0.1}}}"
export I6_GATEWAY_TCP_PORT="${I6_GATEWAY_TCP_PORT:-${GATEWAY_TCP_PORT:-19090}}"
export I6_GATEWAY_NODE_ID="${I6_GATEWAY_NODE_ID:-${GATEWAY_NODE_ID:-gateway-node-001}}"
export I6_AGENT_ID="${I6_AGENT_ID:-${AGENT_ID:-agent-worker-001}}"
export AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN:-local-agent-onboarding-token-change-me}"
export I6_AGENT_CAPABILITIES="${I6_AGENT_CAPABILITIES:-${AGENT_CAPABILITIES:-}}"
export I6_AGENT_EXIT_AFTER_DISPATCH="${I6_AGENT_EXIT_AFTER_DISPATCH:-0}"
export I6_AGENT_MIN_DISPATCHES="${I6_AGENT_MIN_DISPATCHES:-0}"
export I6_AGENT_CALLBACK_REPLAY_ENABLED="${I6_AGENT_CALLBACK_REPLAY_ENABLED:-1}"
export I6_AGENT_PENDING_CALLBACKS_FILE="${I6_AGENT_PENDING_CALLBACKS_FILE:-${ROOT_DIR}/.runtime/agents/pending-callbacks/${I6_AGENT_ID}.json}"

cat <<MSG
[run-task-worker-agent]
  agent:  ${I6_AGENT_ID}
  gateway:${I6_GATEWAY_TCP_HOST}:${I6_GATEWAY_TCP_PORT}
  node:   ${I6_GATEWAY_NODE_ID}
  legacy caps: ${I6_AGENT_CAPABILITIES:-disabled}
  replay: ${I6_AGENT_CALLBACK_REPLAY_ENABLED} store=${I6_AGENT_PENDING_CALLBACKS_FILE}

This is a real TCP mock worker: it waits for TASK_DISPATCH, then sends TASK_ACK, TASK_PROGRESS, and TASK_RESULT according to the mock worker behavior.
Terminal callbacks are stored locally and replayed after reconnect until the Gateway/Core acknowledges them.
Use backend Assignment Profile / Qualification to grant dispatch eligibility. AGENT_CAPABILITIES is legacy diagnostics only.
MSG

exec "${PYTHON_BIN}" "${MOCK_AGENT}" "$@"
