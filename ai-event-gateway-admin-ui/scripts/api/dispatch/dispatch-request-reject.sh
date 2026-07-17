#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
DISPATCH_REQUEST_ID="${1:?dispatchRequestId is required}"
REASON="${2:-Rejected by reviewer.}"
curl -sS -X POST "$BASE_URL/api/dispatch-requests/$DISPATCH_REQUEST_ID/reject" \
  -H 'Content-Type: application/json' \
  -d "{\"reason\":\"$REASON\"}"
echo
