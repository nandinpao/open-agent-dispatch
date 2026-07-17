#!/usr/bin/env bash
set -euo pipefail

CORE_BASE_URL="${CORE_BASE_URL:-http://127.0.0.1:18080}"
TASK_ID="${P2P_CMS_TASK_ID:-p2p-cms-content-review-task}"
AGENT_ID="${P2P_CMS_AGENT_ID:-p2p-cms-review-agent-001}"
URL="${CORE_BASE_URL%/}/admin/tasks/${TASK_ID}/eligible-agents?limit=50"

payload="$(curl -fsS "${URL}")"
P2P_RESPONSE_JSON="${payload}" python3 - "${AGENT_ID}" <<'PY'
import json
import os
import sys
agent_id = sys.argv[1]
payload = json.loads(os.environ["P2P_RESPONSE_JSON"])
eligible = payload.get('eligibleAgents') or []
blocked = payload.get('blockedAgents') or []
requirements = payload.get('requirements') or {}
if requirements.get('sourceSystem') != 'CMS' or requirements.get('taskType') != 'CMS_CONTENT_REVIEW':
    raise SystemExit(f"[FAIL] Unexpected requirements source/task: {requirements}")
match = next((candidate for candidate in eligible if candidate.get('agentId') == agent_id and candidate.get('eligible') is True), None)
if not match:
    blocked_match = next((candidate for candidate in blocked if candidate.get('agentId') == agent_id), None)
    if blocked_match:
        checks = [c for c in blocked_match.get('checks', []) if c.get('blocking')]
        raise SystemExit(f"[FAIL] {agent_id} is blocked. Blocking checks: {checks}")
    raise SystemExit(f"[FAIL] {agent_id} not found in eligibleAgents. Eligible IDs: {[c.get('agentId') for c in eligible]}")
print(f"[OK] {agent_id} is eligible for {payload.get('taskId')} with score={match.get('score')} matchedProfiles={match.get('matchedProfiles')}")
PY
