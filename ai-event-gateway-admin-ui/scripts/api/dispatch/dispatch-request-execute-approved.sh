#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
LIMIT="${1:-50}"
curl -sS -X POST "${BASE_URL}/api/dispatch-requests/execute-approved?limit=${LIMIT}" \
  -H 'Content-Type: application/json'
echo
