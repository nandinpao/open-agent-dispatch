#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = [
    ("Flyway V45", ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V45__p9_remediation_execution_idempotency.sql", ["agent_remediation_workflow_action_executions", "idempotency_key", "uq_agent_remediation_action_idempotency"]),
    ("Action PO", ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/remediation/po/AgentRemediationWorkflowActionExecutionPo.java", ["actionExecutionId", "idempotencyKey", "attemptCount"]),
    ("DAO methods", ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/remediation/dao/AgentRemediationWorkflowDao.java", ["insertActionExecutionIfAbsent", "claimActionExecutionForRun", "completeActionExecutionIfRunning"]),
    ("MyBatis mapper", ROOT / "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent.remediation/AgentRemediationWorkflowDao.xml", ["AgentRemediationWorkflowActionExecutionPoMap", "on conflict (workflow_id, action_id) do nothing", "status in ('PENDING', 'FAILED')"]),
    ("Controller idempotency", ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java", ["ensureActionExecutionRows", "alreadyCompletedActionResult", "remediationActionIdempotencyKey", "actionExecutions"]),
    ("Execution policy", ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationWorkflowExecutionPolicy.java", ["isCompletedActionStatus"]),
    ("Core metadata", ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java", ["agentRemediationWorkflowActionLevelIdempotencyEnabled", "partialSuccessContinuation", "agentRemediationActionExecutionStatuses"]),
    ("Admin UI type", ROOT / "ai-event-gateway-admin-ui/lib/types/core.ts", ["CoreAgentRemediationWorkflowActionExecution", "actionExecutions"]),
    ("Admin UI panel", ROOT / "ai-event-gateway-admin-ui/components/agents/AgentRemediationPanel.tsx", ["Action-level Execution State", "Execute with Lease"]),
]

failed = False
for name, path, needles in checks:
    if not path.exists():
        print(f"[FAIL] {name}: missing {path.relative_to(ROOT)}")
        failed = True
        continue
    text = path.read_text(errors="ignore")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        print(f"[FAIL] {name}: missing {missing}")
        failed = True
    else:
        print(f"[ OK ] {name}")

sys.exit(1 if failed else 0)
