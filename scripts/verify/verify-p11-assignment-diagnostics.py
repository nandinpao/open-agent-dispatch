#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]

def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)

def require(rel: str, *tokens: str) -> None:
    path = ROOT / rel
    if not path.is_file():
        fail(f"missing file: {rel}")
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            fail(f"{rel} missing contract: {token}")

require('ai-event-gateway-admin-ui/hooks/useAgentDetail.ts',
        'getAgent(agentId)', 'scopedTenantId', 'getAssignmentProfiles(undefined, true, scopedTenantId)', 'dispatchGovernanceApi.sourceAssignments', 'sourceAssignments')
require('ai-event-gateway-admin-ui/hooks/useAgentGovernanceList.ts',
        'dispatchGovernanceApi.sourceAssignments', 'sourceAssignments')
require('ai-event-gateway-admin-ui/components/agents/AgentTable.tsx',
        'No ACTIVE and APPROVED Agent Source Coverage', 'Assign Source Coverage', 'source-coverage')
require('ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx',
        'Authoritative Source Coverage', 'Legacy Task Scope Diagnostics', 'SOURCE_ASSIGNMENT_NOT_APPROVED')
require('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/GenericDispatchEligibilityService.java',
        'agent_assignment_eligibility_pass', 'agent_assignment_eligibility_blocked', 'requiredCapabilities', 'requiredOperations')
require('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java',
        'generic_dispatch_authoritative_no_selection', 'generic_dispatch_authoritative_completed', 'candidateTrace')
require('ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java',
        'agent_readiness_blocked', 'agent_readiness_pass')
require('ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/operational/AgentOperationalViewService.java',
        'agent_operational_readiness_blocked', 'agent_setup_readiness_unavailable', 'agent_dispatch_eligibility_unavailable')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java',
        'agent_assignment_eligibility_blocked', 'agent_operational_readiness_blocked')
require('scripts/diagnostics/explain-dispatch-failure.py',
        'PRIMARY BLOCKER: NO_ACTIVE_FLOW_RULE', 'AGENT EVALUATION: NOT OBSERVED', 'RECOVERY LOOP')

result = subprocess.run([sys.executable, str(ROOT / 'scripts/diagnostics/explain-dispatch-failure.py'), '--help'],
                        cwd=ROOT, stdout=subprocess.DEVNULL)
if result.returncode:
    fail('dispatch diagnostic CLI --help failed')

print('[PASS] P11 assignment and agent-block diagnostics contracts verified.')
