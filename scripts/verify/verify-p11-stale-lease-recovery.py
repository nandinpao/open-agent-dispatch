#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = [
    ("service", ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationWorkflowStaleLeaseRecoveryService.java", ["EXECUTION_LEASE_STALE_RECOVERED", "clearExpiredWorkflowExecutionLeaseForOwner", "findExpiredWorkflowExecutionLeases"]),
    ("scheduler", ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ScheduledAgentRemediationWorkflowLeaseRecovery.java", ["@Scheduled", "stale-lease-reaper", "recoverExpiredLeases"]),
    ("controller", ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationWorkflowLeaseRecoveryController.java", ["/admin/remediation/workflow-leases", "/stale", "/recovered", "/recover-stale"]),
    ("dao", ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/remediation/dao/AgentRemediationWorkflowDao.java", ["findExpiredWorkflowExecutionLeases", "findHistoryByEventType", "clearExpiredWorkflowExecutionLeaseForOwner"]),
    ("mapper", ROOT / "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent.remediation/AgentRemediationWorkflowDao.xml", ["findExpiredWorkflowExecutionLeases", "findHistoryByEventType", "clearExpiredWorkflowExecutionLeaseForOwner"]),
    ("migration", ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V47__p11_remediation_workflow_stale_lease_recovery.sql", ["idx_agent_remediation_workflows_stale_execution_lease", "idx_agent_remediation_workflow_history_event_occurred"]),
    ("status", ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java", ["agentRemediationWorkflowStaleLeaseRecoveryEnabled", "agentRemediationWorkflowStaleLeaseQueueEndpoints"]),
    ("admin endpoints", ROOT / "ai-event-gateway-admin-ui/lib/api/endpoints.ts", ["agentRemediationWorkflowStaleLeases", "agentRemediationWorkflowRecoveredLeases", "agentRemediationWorkflowRecoverStaleLeases"]),
    ("admin panel", ROOT / "ai-event-gateway-admin-ui/components/agents/AgentRemediationPanel.tsx", ["Stale Workflow Lease Queue", "Recover Stale Leases", "listStaleAgentRemediationWorkflowLeases"]),
]
failed = False
for label, path, needles in checks:
    if not path.exists():
        print(f"[FAIL] {label}: missing {path}")
        failed = True
        continue
    text = path.read_text(errors="ignore")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {label}: missing {needle}")
            failed = True
if failed:
    sys.exit(1)
print("P11 stale lease recovery static verification passed.")
