#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def require(path, needle=None, label=None):
    p = ROOT / path
    ok = p.exists()
    if ok and needle is not None:
        text = p.read_text(encoding="utf-8")
        ok = needle in text
    checks.append((label or str(path), ok))

require("ai-event-gateway-core/database-platform/src/main/resources/db/migration/V44__p7_persistent_remediation_workflow_ha.sql", "version bigint", "Flyway V44 adds workflow version")
require("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/remediation/dao/AgentRemediationWorkflowDao.java", "updateWorkflowStatusIfCurrent", "MyBatis DAO has optimistic status transition")
require("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/remediation/po/AgentRemediationWorkflowPo.java", "rollbackSuggestionsJson", "Workflow PO carries JSON payloads")
require("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/remediation/po/AgentRemediationWorkflowHistoryPo.java", "occurredAt", "Workflow history PO exists")
require("ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent.remediation/AgentRemediationWorkflowDao.xml", "agent_remediation_workflows", "MyBatis XML maps workflow table")
require("ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent.remediation/AgentRemediationWorkflowDao.xml", "and status = #{expectedStatus}", "Status update is compare-and-set guarded")
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java", "remediationWorkflowDao.findWorkflowsByAgentId", "Controller lists workflows from repository")
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java", "ConcurrentHashMap", "Controller must not use in-process workflow map")
# invert last check
checks[-1] = (checks[-1][0], not checks[-1][1])
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java", "agentRemediationWorkflowRepository", "Core status exposes persistent workflow repository")

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + " - " + name)
if failed:
    print("\nP7 verification failed:")
    for name in failed:
        print(" - " + name)
    sys.exit(1)
print("\nP7 remediation persistence verification passed.")
