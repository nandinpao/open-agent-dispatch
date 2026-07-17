#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT="${ROOT_DIR}/scripts/netty-tcp-agent-client.js"
EVENT_TYPE="${1:-}"
ENTITY_ID="${2:-demo-event-001}"
NODE_INDEX="${3:-1}"

if [[ -z "${EVENT_TYPE}" ]]; then
  echo "Usage: $0 {task-requested|agent-heartbeat} <task-or-agent-id> [node-index]" >&2
  exit 2
fi

if ! command -v node >/dev/null 2>&1; then
  echo "ERROR: node is required for cluster-send-event.sh." >&2
  exit 1
fi

BASE_TCP_PORT="${GATEWAY_TCP_BASE_PORT:-19090}"
TCP_PORT_STEP="${GATEWAY_TCP_PORT_STEP:-2}"
GATEWAY_HOST="${GATEWAY_HOST:-127.0.0.1}"
GATEWAY_TCP_PORT="${GATEWAY_TCP_PORT:-$((BASE_TCP_PORT + (NODE_INDEX - 1) * TCP_PORT_STEP))}"
GATEWAY_NODE_ID="${GATEWAY_NODE_ID:-$(printf 'gateway-node-%03d' "${NODE_INDEX}")}"
AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN:-local-agent-onboarding-token-change-me}"

case "${EVENT_TYPE}" in
  task-requested)
    export AGENT_ID="${AGENT_ID:-agent-event-node-$(printf '%03d' "${NODE_INDEX}")}"
    export TASK_ID="${ENTITY_ID}"
    ;;
  agent-heartbeat)
    export AGENT_ID="${ENTITY_ID}"
    ;;
  *)
    echo "Unsupported event type: ${EVENT_TYPE}" >&2
    echo "Supported: task-requested, agent-heartbeat" >&2
    exit 2
    ;;
esac

export AGENT_CLIENT_MODE=send
export GATEWAY_HOST GATEWAY_TCP_HOST="${GATEWAY_HOST}" GATEWAY_TCP_PORT GATEWAY_NODE_ID AGENT_ONBOARDING_TOKEN
node "${CLIENT}" send "${EVENT_TYPE}" "${ENTITY_ID}"
