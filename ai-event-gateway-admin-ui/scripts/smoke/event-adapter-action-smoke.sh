#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
THRESHOLD="${1:-3}"
AGENT_ID="${2:-agent-tnn-001}"
GATEWAY_ID="${3:-gateway-tnn-001}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"${SCRIPT_DIR}/event-result-relay-smoke.sh" "${THRESHOLD}" "${AGENT_ID}" "${GATEWAY_ID}"
echo "adapter actions:"
curl -sS "${BASE_URL}/api/adapter-actions?limit=50"
echo
