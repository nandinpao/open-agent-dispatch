#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
EXPECTED="MYBATIS"

echo "[p12.2] Checking core status at ${BASE_URL}..."
STATUS_JSON="$(curl -fsS "${BASE_URL}/api/core/status")"

echo "$STATUS_JSON" | python3 - <<'PY'
import json, sys
status = json.load(sys.stdin)
required = {
    "incidentStore": "MYBATIS",
    "taskStore": "MYBATIS",
    "taskCallbackStore": "MYBATIS",
    "gatewayNodeStore": "MYBATIS",
    "agentDirectoryStore": "MYBATIS",
    "dispatchRequestStore": "MYBATIS",
}
missing = []
for key, expected in required.items():
    actual = status.get(key)
    if actual != expected:
        missing.append(f"{key}: expected {expected}, got {actual}")
if missing:
    raise SystemExit("\n".join(missing))
print("Store mode check passed")
PY

echo "[p12.2] Running gateway/agent smoke through MyBatis repositories..."
"$(dirname "$0")/gateway-agent-directory-smoke.sh" gateway-node-mybatis-p122 openclaw-agent-mybatis-p122 ws-session-mybatis-p122

echo "[p12.2] Running event intake smoke through incident/task MyBatis repositories..."
"$(dirname "$0")/event-intake-smoke.sh"

echo "[p12.2] MyBatis repository expansion smoke completed."
