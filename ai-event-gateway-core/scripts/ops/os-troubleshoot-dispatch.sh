#!/usr/bin/env bash
set -euo pipefail
CORE_BASE_URL="${CORE_BASE_URL:-${I7_CORE_BASE_URL:-http://127.0.0.1:18080}}"
DISPATCH_REQUEST_ID="${DISPATCH_REQUEST_ID:-}"
CURL=(curl -fsS --max-time "${CURL_TIMEOUT_SECONDS:-10}")

if [ -n "$DISPATCH_REQUEST_ID" ]; then
  echo "[dispatch] dispatchRequestId=${DISPATCH_REQUEST_ID}"
  "${CURL[@]}" "${CORE_BASE_URL}/api/dispatch-requests/${DISPATCH_REQUEST_ID}" | python3 -m json.tool || true
else
  echo "[dispatch] latest dispatch requests"
  "${CURL[@]}" "${CORE_BASE_URL}/api/dispatch-requests?limit=${LIMIT:-20}" | python3 -m json.tool || true
fi

echo "[dispatch] diagnostics"
echo "- PENDING/APPROVED not moving: check execute-approved scheduler/API and dispatch.client.auto-execute-approved."
echo "- DELIVERY_FAILED: check Netty delivery endpoint, ownerGatewayNodeId, agentSessionId, and X-Cluster-Token."
echo "- DISPATCHING stuck: check I3 claim lease and timeout recovery worker."
