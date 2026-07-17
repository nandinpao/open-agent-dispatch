#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
INCIDENT_ID="${1:?incidentId is required}"
LIMIT="${2:-100}"
curl -sS "${BASE_URL}/api/tasks/incident/${INCIDENT_ID}?limit=${LIMIT}" | jq .
