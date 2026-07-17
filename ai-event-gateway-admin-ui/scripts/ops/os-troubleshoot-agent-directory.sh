#!/usr/bin/env bash
set -euo pipefail
CORE_BASE_URL="${CORE_BASE_URL:-${I7_CORE_BASE_URL:-http://127.0.0.1:18080}}"
GATEWAY_NODE_ID="${GATEWAY_NODE_ID:-${I7_GATEWAY_NODE_ID:-gateway-node-001}}"
AGENT_ID="${AGENT_ID:-}"
CURL=(curl -fsS --max-time "${CURL_TIMEOUT_SECONDS:-10}")

echo "[agent-directory] gateway=${GATEWAY_NODE_ID} agent=${AGENT_ID:-<all>}"
if [ -n "$AGENT_ID" ]; then
  "${CURL[@]}" "${CORE_BASE_URL}/api/gateway-nodes/${GATEWAY_NODE_ID}/agents" | python3 -m json.tool | grep -C 8 "$AGENT_ID" || true
else
  "${CURL[@]}" "${CORE_BASE_URL}/api/gateway-nodes/${GATEWAY_NODE_ID}/agents" | python3 -m json.tool || true
fi

echo "[agent-directory] diagnostics"
echo "- If Netty sees the agent but Core does not, check GATEWAY_CORE_DIRECTORY_SYNC_ENABLED and X-Cluster-Token."
echo "- If Core shows EXPIRED/OFFLINE, check gateway heartbeat and snapshot intervals."
