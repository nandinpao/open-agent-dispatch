#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
LIMIT="${1:-100}"
curl -sS "${BASE_URL}/api/dispatch-requests/status/APPROVED?limit=${LIMIT}"
echo
