#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
INCIDENT_ID="${1:?Usage: incident-summary.sh <incidentId>}"
curl -sS "$BASE_URL/api/incidents/$INCIDENT_ID/occurrence-summary" | jq .
