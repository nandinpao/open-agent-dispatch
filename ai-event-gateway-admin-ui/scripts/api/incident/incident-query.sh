#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
QUERY="${1:-}"
if [ -n "$QUERY" ]; then
  curl -sS "$BASE_URL/api/incidents?$QUERY" | jq .
else
  curl -sS "$BASE_URL/api/incidents" | jq .
fi
