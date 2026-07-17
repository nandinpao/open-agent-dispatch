#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

checks = []

def require(path: str, needle: str, label: str) -> None:
    text = (ROOT / path).read_text(encoding='utf-8')
    if needle not in text:
        raise AssertionError(f"Missing {label}: {needle} in {path}")
    checks.append(label)

# Migration and database contract.
require('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V46__p10_remediation_workflow_execution_lease.sql',
        'execution_lease_owner', 'V46 execution_lease_owner column')
require('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V46__p10_remediation_workflow_execution_lease.sql',
        'idx_agent_remediation_workflows_execution_lease', 'V46 lease index')

# MyBatis XML must parse and expose lease methods.
mapper = ROOT / 'ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent.remediation/AgentRemediationWorkflowDao.xml'
ET.parse(mapper)
mapper_text = mapper.read_text(encoding='utf-8')
for method in ['acquireWorkflowExecutionLease', 'releaseWorkflowExecutionLease', 'clearExpiredWorkflowExecutionLease']:
    if method not in mapper_text:
        raise AssertionError(f'Missing mapper method {method}')
    checks.append(f'mapper {method}')
if 'execution_lease_expires_at &lt;= #{acquiredAt}' not in mapper_text:
    raise AssertionError('Lease acquire query must allow stale lease takeover by expiry')
checks.append('stale lease takeover predicate')

# Java DAO/PO/controller contract.
require('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/remediation/po/AgentRemediationWorkflowPo.java',
        'private String executionLeaseOwner;', 'PO executionLeaseOwner')
require('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/remediation/dao/AgentRemediationWorkflowDao.java',
        'int acquireWorkflowExecutionLease', 'DAO acquireWorkflowExecutionLease')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationWorkflowExecutionPolicy.java',
        'WORKFLOW_EXECUTION_LEASE_DURATION', 'execution policy lease duration')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java',
        'EXECUTION_LEASE_ACQUIRED', 'history lease acquired')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java',
        'EXECUTION_LEASE_BUSY', 'history lease busy')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationWorkflowExecutionPolicy.java',
        'P10_WORKFLOW_LEASED_ACTION_LEVEL_EXECUTION', 'leased action-level execution mode')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java',
        'agentRemediationWorkflowExecutionLeaseEnabled', 'Core status lease flag')

# Admin UI visibility.
require('ai-event-gateway-admin-ui/lib/types/core.ts', 'executionLeaseActive?: boolean;', 'Admin UI lease type')
require('ai-event-gateway-admin-ui/components/agents/AgentRemediationPanel.tsx', 'Workflow execution lease', 'Admin UI lease panel')
require('ai-event-gateway-admin-ui/components/agents/AgentRemediationPanel.tsx', 'Execute with Lease', 'Admin UI execute label')

print(f'P10 remediation execution lease verification passed ({len(checks)} checks).')
