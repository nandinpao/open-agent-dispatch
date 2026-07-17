#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
AGENT_ID="${1:-agent-tnn-001}"
SITE_ID="${2:-TNN}"
GATEWAY_ID="${3:-gateway-tnn-001}"
curl -sS -X POST "$BASE_URL/api/agents/register" \
  -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\",\"agentType\":\"OPENCLAW\",\"ownerGatewayNodeId\":\"$GATEWAY_ID\",\"siteId\":\"$SITE_ID\",\"siteName\":\"${SITE_ID} 機房\",\"status\":\"IDLE\",\"capabilities\":[\"incident-analysis\",\"mes-analysis\"],\"currentTaskCount\":0,\"maxConcurrentTasks\":3,\"healthScore\":95}"
echo
