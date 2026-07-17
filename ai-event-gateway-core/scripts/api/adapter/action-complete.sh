#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
ACTION_ID="${1:-}"
RESPONSE_REF="${2:-mock-response-ref}"
if [ -z "${ACTION_ID}" ]; then
  echo "Usage: $0 <actionId> [responseRef]" >&2
  exit 1
fi
curl -sS -X POST "${BASE_URL}/api/adapter-actions/${ACTION_ID}/complete" \
  -H 'Content-Type: application/json' \
  -d "{\"responseRef\":\"${RESPONSE_REF}\"}"
echo
