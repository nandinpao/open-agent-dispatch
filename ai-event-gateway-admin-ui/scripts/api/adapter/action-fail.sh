#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
ACTION_ID="${1:-}"
ERROR="${2:-mock adapter action failure}"
if [ -z "${ACTION_ID}" ]; then
  echo "Usage: $0 <actionId> [error]" >&2
  exit 1
fi
curl -sS -X POST "${BASE_URL}/api/adapter-actions/${ACTION_ID}/fail" \
  -H 'Content-Type: application/json' \
  -d "{\"error\":\"${ERROR}\"}"
echo
