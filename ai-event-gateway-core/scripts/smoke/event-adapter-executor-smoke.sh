#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
THRESHOLD="${1:-3}"
AGENT_ID="${2:-agent-tnn-001}"
GATEWAY_ID="${3:-gateway-tnn-001}"

# This smoke test reuses the P6 netty-dispatch flow to produce a dispatch request.
# It requires either a reachable mock gateway endpoint or a manually prepared DISPATCHED task callback path.
"${SCRIPT_DIR}/event-netty-dispatch-smoke.sh" "${THRESHOLD}" "${AGENT_ID}" "${GATEWAY_ID}" || true

echo "Recent adapter actions before execution:"
"${ROOT_DIR}/scripts/api/adapter/action-query.sh" || true

echo "Execute pending adapter actions with mock executors:"
"${ROOT_DIR}/scripts/api/adapter/action-execute-pending.sh" 50

echo "Recent adapter actions after execution:"
"${ROOT_DIR}/scripts/api/adapter/action-query.sh" || true
