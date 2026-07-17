#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
TASK_ID="${1:-}"
LIMIT="${2:-100}"
if [ -z "${TASK_ID}" ]; then
  echo "Usage: $0 <taskId> [limit]" >&2
  exit 1
fi
curl -sS "${BASE_URL}/api/adapter-actions/task/${TASK_ID}?limit=${LIMIT}"
echo
