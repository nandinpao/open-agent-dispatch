#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
DISPATCH_REQUEST_ID="${1:-}"
if [ -z "${DISPATCH_REQUEST_ID}" ]; then
  echo "Usage: $0 <dispatchRequestId>" >&2
  exit 1
fi
curl -sS -X POST "${BASE_URL}/api/dispatch-requests/${DISPATCH_REQUEST_ID}/execute" \
  -H 'Content-Type: application/json'
echo
