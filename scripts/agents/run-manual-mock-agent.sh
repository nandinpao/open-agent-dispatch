#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# This script is intentionally a thin compatibility wrapper around the original,
# working Netty long-connection simulator. Do not bootstrap a separate I6/mock
# Python agent here; that uses a different registration contract and can be
# rejected by Netty in local Docker tests.
#
# Preferred direct command:
#   CORE_BOOTSTRAP_AGENTS=true \
#   CORE_BOOTSTRAP_FORCE_ISSUE_CREDENTIAL=true \
#   CORE_BOOTSTRAP_VERIFY_AUTHORIZATION=true \
#   CORE_BASE_URL=http://baofire.com:18080 \
#   CORE_BOOTSTRAP_ENV_FILE=deploy/env/.env.local \
#   CORE_BOOTSTRAP_ADMIN_AUTH_MODE=SESSION \
#   AGENTS_PER_NODE=3 \
#   AGENT_ONBOARDING_TOKEN=local-agent-onboarding-token-change-me \
#   ./scripts/cluster-run-many-agents.sh restart

PUBLIC_HOST="${OPENDISPATCH_PUBLIC_HOST:-${PUBLIC_HOST:-}}"
PUBLIC_SCHEME="${OPENDISPATCH_PUBLIC_SCHEME:-http}"

if [[ -n "${PUBLIC_HOST}" ]]; then
  export CORE_BASE_URL="${CORE_BASE_URL:-${I6_CORE_BASE_URL:-${PUBLIC_SCHEME}://${PUBLIC_HOST}:18080}}"
  export GATEWAY_HOST="${GATEWAY_HOST:-${I6_GATEWAY_TCP_HOST:-${PUBLIC_HOST}}}"
else
  export CORE_BASE_URL="${CORE_BASE_URL:-${I6_CORE_BASE_URL:-http://127.0.0.1:18080}}"
  export GATEWAY_HOST="${GATEWAY_HOST:-${I6_GATEWAY_TCP_HOST:-127.0.0.1}}"
fi

export CORE_BOOTSTRAP_ENV_FILE="${CORE_BOOTSTRAP_ENV_FILE:-${ENV_FILE:-deploy/env/.env.local}}"
if [[ ! -f "${CORE_BOOTSTRAP_ENV_FILE}" ]]; then
  export CORE_BOOTSTRAP_ENV_FILE="deploy/env/.env.local.example"
fi
export CORE_BOOTSTRAP_ADMIN_AUTH_MODE="${CORE_BOOTSTRAP_ADMIN_AUTH_MODE:-SESSION}"
export CORE_BOOTSTRAP_AGENTS="${CORE_BOOTSTRAP_AGENTS:-true}"
export CORE_BOOTSTRAP_RESET_BLOCKED="${CORE_BOOTSTRAP_RESET_BLOCKED:-false}"
export CORE_BOOTSTRAP_FORCE_ISSUE_CREDENTIAL="${CORE_BOOTSTRAP_FORCE_ISSUE_CREDENTIAL:-true}"
export CORE_BOOTSTRAP_VERIFY_AUTHORIZATION="${CORE_BOOTSTRAP_VERIFY_AUTHORIZATION:-true}"
export GATEWAY_CLUSTER_NODE_COUNT="${GATEWAY_CLUSTER_NODE_COUNT:-1}"
export AGENTS_PER_NODE="${AGENTS_PER_NODE:-1}"
export GATEWAY_TCP_BASE_PORT="${GATEWAY_TCP_BASE_PORT:-${I6_GATEWAY_TCP_PORT:-19090}}"
export AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN:-${NETTY_AGENT_ONBOARDING_TOKEN:-local-agent-onboarding-token-change-me}}"
export AGENT_LEGACY_CAPABILITIES_ENABLED="${AGENT_LEGACY_CAPABILITIES_ENABLED:-false}"
export AGENT_CAPABILITIES="${AGENT_CAPABILITIES:-${I6_AGENT_CAPABILITIES:-}}"
export AGENT_CORE_CAPABILITIES="${AGENT_CORE_CAPABILITIES:-${AGENT_CAPABILITIES}}"
export AGENT_HEARTBEAT_INTERVAL_MS="${AGENT_HEARTBEAT_INTERVAL_MS:-${I6_AGENT_HEARTBEAT_INTERVAL_MS:-5000}}"

CMD="${1:-restart}"
shift || true

echo "[manual-agent] delegating to original cluster simulator"
echo "[manual-agent] core=${CORE_BASE_URL} gateway=${GATEWAY_HOST}:${GATEWAY_TCP_BASE_PORT} nodes=${GATEWAY_CLUSTER_NODE_COUNT} agentsPerNode=${AGENTS_PER_NODE}"
echo "[manual-agent] token must match Netty container AGENT_ONBOARDING_TOKEN; default local-agent-onboarding-token-change-me"

exec "${ROOT_DIR}/scripts/cluster-run-many-agents.sh" "${CMD}" "$@"
