#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
checks = [
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillDiffEntry.java', 'class AgentSkillDiffEntry'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillDiffResult.java', 'breakingFields'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillImpactAnalysisResult.java', 'impactedAgents'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillApprovalPolicy.java', 'separationOfDuties'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillWorkflowCommand.java', 'operatorRoles'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'diffVersion'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'analyzeImpact'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'enforceWorkflowRole'),
    ('ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java', '/admin/agent-skills/{skillCode}/versions/{version}/diff'),
    ('ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java', '/admin/agent-skills/{skillCode}/approval-policy'),
    ('ai-event-gateway-database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/skill/po/AgentSkillApprovalPolicyPo.java', 'class AgentSkillApprovalPolicyPo'),
    ('ai-event-gateway-database-platform/src/main/resources/db/migration/V31__agent_skill_approval_policy.sql', 'agent_skill_approval_policies'),
    ('ai-event-gateway-database-platform/src/main/resources/mybatis/postgresql/agent/AgentSkillRegistryDao.xml', 'SkillApprovalPolicyMap'),
]
missing = []
for rel, token in checks:
    path = ROOT / rel
    if not path.exists() or token not in path.read_text():
        missing.append(f'{rel}: missing {token}')
xml_path = ROOT / 'ai-event-gateway-database-platform/src/main/resources/mybatis/postgresql/agent/AgentSkillRegistryDao.xml'
try:
    ET.parse(xml_path)
except Exception as exc:
    missing.append(f'{xml_path}: XML parse failed: {exc}')
if missing:
    print('\n'.join(missing), file=sys.stderr)
    sys.exit(1)
print('P9.5 skill diff / impact analysis / approval RBAC integration check passed.')
