#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
checks = [
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentCapabilityDriftItem.java', 'class AgentCapabilityDriftItem'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentCapabilityDriftReport.java', 'driftTypeCounts'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillDeprecationPlan.java', 'replacementSkillCodes'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillDeprecationMigrationPlan.java', 'blockingReasons'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'detectFleetDrift'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'analyzeDeprecationMigrationPlan'),
    ('ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java', '/admin/agent-skills/drift'),
    ('ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java', '/admin/agent-skills/{skillCode}/deprecation-plan'),
    ('ai-event-gateway-database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/skill/po/AgentSkillDeprecationPlanPo.java', 'class AgentSkillDeprecationPlanPo'),
    ('ai-event-gateway-database-platform/src/main/resources/db/migration/V32__agent_skill_deprecation_plans.sql', 'agent_skill_deprecation_plans'),
    ('ai-event-gateway-database-platform/src/main/resources/mybatis/postgresql/agent/AgentSkillRegistryDao.xml', 'SkillDeprecationPlanMap'),
]
missing = []
for rel, token in checks:
    path = ROOT / rel
    if not path.exists() or token not in path.read_text():
        missing.append(f'{rel}: missing {token}')
try:
    ET.parse(ROOT / 'ai-event-gateway-database-platform/src/main/resources/mybatis/postgresql/agent/AgentSkillRegistryDao.xml')
except Exception as exc:
    missing.append(f'AgentSkillRegistryDao.xml: XML parse failed: {exc}')
if missing:
    print('\\n'.join(missing), file=sys.stderr)
    sys.exit(1)
print('P9.6 capability drift / skill deprecation migration integration check passed.')
