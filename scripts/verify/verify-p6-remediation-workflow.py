#!/usr/bin/env python3
"""Verify the current remediation approval workflow contract.

This verifier intentionally checks current product contracts rather than legacy
phase wording in the Admin UI. Visible UI text should not depend on phase labels.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
checks = {
    'core controller workflow list route': ('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java', '/remediation/workflows'),
    'core controller approve route': ('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java', '/approve'),
    'core status guardrails': ('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java', 'agentRemediationApprovalWorkflowEnabled'),
    'security event type': ('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentSecurityEventType.java', 'AGENT_REMEDIATION_WORKFLOW_EXECUTED'),
    'flyway V43': ('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V43__p6_agent_remediation_approval_workflow.sql', 'agent_remediation_workflows'),
    'admin endpoints': ('ai-event-gateway-admin-ui/lib/api/endpoints.ts', 'agentRemediationWorkflowExecute'),
    'admin client': ('ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts', 'executeAgentRemediationWorkflow'),
    'admin types': ('ai-event-gateway-admin-ui/lib/types/core.ts', 'CoreAgentRemediationWorkflow'),
    'admin panel': ('ai-event-gateway-admin-ui/components/agents/AgentRemediationPanel.tsx', 'Agent Remediation Workflow'),
    'contract test': ('ai-event-gateway-admin-ui/tests/core-admin-contract.test.ts', 'agentRemediationWorkflowExecute'),
}

missing = []
for name, (rel, token) in checks.items():
    path = root / rel
    if not path.exists() or token not in path.read_text(encoding='utf-8'):
        missing.append(f'{name}: {rel} missing token {token!r}')

if missing:
    raise SystemExit('\n'.join(missing))
print('Remediation approval workflow integration check passed.')
