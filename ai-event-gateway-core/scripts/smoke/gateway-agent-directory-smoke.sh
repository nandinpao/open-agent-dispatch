#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
GATEWAY_NODE_ID="${1:-gateway-node-001}"
AGENT_ID="${2:-openclaw-agent-001}"
SESSION_ID="${3:-ws-session-001}"

printf 'Core status: '
curl -fsS "${BASE_URL}/api/core/status" >/dev/null
echo "OK"

echo "Registering gateway node ${GATEWAY_NODE_ID}"
cat <<JSON | curl -fsS -X POST "${BASE_URL}/internal/gateway-nodes/register" \
  -H 'Content-Type: application/json' \
  --data-binary @- >/dev/null
{
  "gatewayNodeId": "${GATEWAY_NODE_ID}",
  "nodeName": "${GATEWAY_NODE_ID}",
  "hostName": "localhost",
  "advertiseHost": "127.0.0.1",
  "httpPort": 18081,
  "wsPort": 18082,
  "siteId": "TNN",
  "region": "TW",
  "zone": "TNN-A",
  "version": "netty-local-smoke",
  "metadata": {
    "smoke": true
  }
}
JSON

echo "Connecting agent ${AGENT_ID} session ${SESSION_ID} through ${GATEWAY_NODE_ID}"
cat <<JSON | curl -fsS -X POST "${BASE_URL}/internal/gateway-nodes/${GATEWAY_NODE_ID}/agents/${AGENT_ID}/connected" \
  -H 'Content-Type: application/json' \
  --data-binary @- >/dev/null
{
  "agentId": "${AGENT_ID}",
  "agentType": "OPENCLAW",
  "agentSessionId": "${SESSION_ID}",
  "siteId": "TNN",
  "status": "IDLE",
  "capabilities": ["incident-analysis", "issue-tracking"],
  "currentTaskCount": 0,
  "maxConcurrentTasks": 2,
  "healthScore": 100
}
JSON

cat <<JSON | curl -fsS -X POST "${BASE_URL}/internal/gateway-nodes/${GATEWAY_NODE_ID}/agents/${AGENT_ID}/heartbeat" \
  -H 'Content-Type: application/json' \
  --data-binary @- >/dev/null
{
  "status": "IDLE",
  "currentTaskCount": 0,
  "healthScore": 100,
  "agentSessionId": "${SESSION_ID}"
}
JSON

export BASE_URL GATEWAY_NODE_ID AGENT_ID SESSION_ID
python3 <<'PY'
import json
import os
import sys
import urllib.request

base_url = os.environ["BASE_URL"]
gateway_node_id = os.environ["GATEWAY_NODE_ID"]
agent_id = os.environ["AGENT_ID"]
session_id = os.environ["SESSION_ID"]

with urllib.request.urlopen(f"{base_url}/api/gateway-nodes/{gateway_node_id}") as response:
    node_envelope = json.load(response)
node = node_envelope.get('data', node_envelope) if isinstance(node_envelope, dict) else node_envelope
with urllib.request.urlopen(f"{base_url}/api/gateway-nodes/{gateway_node_id}/agents") as response:
    agents_envelope = json.load(response)
agents = agents_envelope.get('data', agents_envelope) if isinstance(agents_envelope, dict) else agents_envelope

errors = []
if node.get("gatewayNodeId") != gateway_node_id:
    errors.append("gateway node was not registered")
matching = [a for a in agents if a.get("agentId") == agent_id]
if len(matching) != 1:
    errors.append(f"expected 1 matching agent, got {len(matching)}")
else:
    agent = matching[0]
    if agent.get("ownerGatewayNodeId") != gateway_node_id:
        errors.append(f"expected ownerGatewayNodeId={gateway_node_id}, got {agent.get('ownerGatewayNodeId')}")
    if agent.get("agentSessionId") != session_id:
        errors.append(f"expected agentSessionId={session_id}, got {agent.get('agentSessionId')}")
    if agent.get("status") not in ("IDLE", "BUSY_ACCEPTING"):
        errors.append(f"expected assignable-ish status, got {agent.get('status')}")

print(json.dumps({"gatewayNode": node, "agents": agents}, ensure_ascii=False, indent=2))
if errors:
    for error in errors:
        print("ERROR:", error, file=sys.stderr)
    sys.exit(1)
PY
