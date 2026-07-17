#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
TASK_ID="${1:?taskId is required}"
LIMIT="${2:-100}"
curl -sS "$BASE_URL/api/dispatch-requests/task/$TASK_ID?limit=$LIMIT"
echo
