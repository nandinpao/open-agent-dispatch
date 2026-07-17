#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
curl -fsS "${BASE_URL}/api/ops/summary" | jq .
