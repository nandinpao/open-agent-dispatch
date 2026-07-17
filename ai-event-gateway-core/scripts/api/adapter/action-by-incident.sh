#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
INCIDENT_ID="${1:-}"
LIMIT="${2:-100}"
if [ -z "${INCIDENT_ID}" ]; then
  echo "Usage: $0 <incidentId> [limit]" >&2
  exit 1
fi
curl -sS "${BASE_URL}/api/adapter-actions/incident/${INCIDENT_ID}?limit=${LIMIT}"
echo
