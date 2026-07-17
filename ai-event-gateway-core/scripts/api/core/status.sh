#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
curl -sS "$BASE_URL/api/core/status" | python3 -m json.tool
