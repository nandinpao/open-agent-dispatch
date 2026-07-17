#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
LIMIT="${1:-100}"
curl -sS -X POST "${BASE_URL}/internal/control-plane/tasks/dispatch-recovery/scan-timeouts?limit=${LIMIT}" \
  -H 'Content-Type: application/json'
echo
