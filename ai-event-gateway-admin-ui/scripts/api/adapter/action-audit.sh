#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
ACTION_ID="${1:?Usage: $0 <actionId> [limit]}"
LIMIT="${2:-100}"
curl -sS "${BASE_URL}/api/adapter-actions/${ACTION_ID}/audit?limit=${LIMIT}"
echo
