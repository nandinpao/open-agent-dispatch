#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:18080}"
GATEWAY_NODE_ID="${1:-gateway-node-mybatis-001}"

curl_json() {
  local method="$1"; shift
  local path="$1"; shift
  curl -fsS -X "$method" "${BASE_URL}${path}" "$@"
}

echo "[p12.1] Core status"
curl_json GET /api/core/status | grep -q 'p12.1-core-database-mybatis-integration'
curl_json GET /api/core/status | grep -q 'MYBATIS'

echo "[p12.1] Register gateway node through MyBatis repository"
curl_json POST /internal/gateway-nodes/register \
  -H 'Content-Type: application/json' \
  -d "{\"gatewayNodeId\":\"${GATEWAY_NODE_ID}\",\"nodeName\":\"MyBatis Smoke Node\",\"hostName\":\"localhost\",\"advertiseHost\":\"localhost\",\"httpPort\":18081,\"wsPort\":19081,\"region\":\"TW\",\"zone\":\"LAB\",\"siteId\":\"TEST\",\"version\":\"p12.1\",\"metadata\":{\"repository\":\"mybatis\"}}" >/dev/null

curl_json GET "/api/gateway-nodes/${GATEWAY_NODE_ID}" | grep -q "${GATEWAY_NODE_ID}"
echo "[p12.1] OK"
