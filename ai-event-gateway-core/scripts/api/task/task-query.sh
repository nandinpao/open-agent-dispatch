#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
QUERY="${1:-limit=100}"
curl -sS "${BASE_URL}/api/tasks?${QUERY}" | jq .
