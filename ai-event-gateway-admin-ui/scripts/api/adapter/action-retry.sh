#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
ACTION_ID="${1:?Usage: adapter-action-retry.sh <actionId>}"
curl -sS -X POST "${BASE_URL}/api/adapter-actions/${ACTION_ID}/retry" | jq .
