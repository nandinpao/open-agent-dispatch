#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Root-level convenience wrapper for local single-Netty development.  The
# authoritative simulator remains under ai-event-gateway-netty/scripts.
# In make ci-local there is one Netty runtime, so default to one simulated
# gateway node and the host-routed TCP endpoint when OPENDISPATCH_PUBLIC_HOST is set.
export GATEWAY_CLUSTER_NODE_COUNT="${GATEWAY_CLUSTER_NODE_COUNT:-1}"
export GATEWAY_TCP_BASE_PORT="${GATEWAY_TCP_BASE_PORT:-${NETTY_TCP_PORT:-19090}}"
if [[ -n "${OPENDISPATCH_PUBLIC_HOST:-}" && -z "${GATEWAY_HOST:-}" ]]; then
  export GATEWAY_HOST="${OPENDISPATCH_PUBLIC_HOST}"
fi

exec "${ROOT_DIR}/ai-event-gateway-netty/scripts/cluster-run-many-agents.sh" "$@"
