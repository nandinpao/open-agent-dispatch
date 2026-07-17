#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

checks = []

def require(path, needle):
    text = (ROOT / path).read_text()
    if needle not in text:
        checks.append(f"missing {needle!r} in {path}")

require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java', 'api_database_failure')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java', 'rootException={}')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java', 'OpenDispatchRequestContextHolder.current()')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentGovernanceController.java', 'admin_agent_enrollment_create_received')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentGovernanceController.java', 'admin_agent_enrollment_create_failed')
require('ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/governance/AgentGovernanceService.java', 'agent_enrollment_submit_started')
require('ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/governance/AgentGovernanceService.java', 'agent_enrollment_submit_failed stage={}')
require('ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/governance/AgentGovernanceService.java', 'FIND_LATEST_ENROLLMENT')
require('ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/governance/AgentGovernanceService.java', 'SAVE_ENROLLMENT')
require('ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js', 'X-Request-Id')
require('ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js', 'X-Correlation-Id')
require('ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js', 'Core log lookup: correlationId=')
require('docs/PHASE19_AGENT_ENROLLMENT_DIAGNOSTIC_LOGGING.md', 'agent_enrollment_submit_failed')

if checks:
    print('Phase 19 agent enrollment diagnostic logging verification failed:')
    for item in checks:
        print(' -', item)
    sys.exit(1)
print('Phase 19 agent enrollment diagnostic logging contract verified.')
