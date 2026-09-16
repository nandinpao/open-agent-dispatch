#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = [
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillVersion.java', 'class AgentSkillVersion'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillAuditEntry.java', 'class AgentSkillAuditEntry'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillLifecycleStatus.java', 'PENDING_APPROVAL'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryRepository.java', 'listVersions'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'createDraftVersion'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'publishVersion'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'rollbackToVersion'),
    ('ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java', '/admin/agent-skills/{skillCode}/versions'),
    ('ai-event-gateway-database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/skill/po/AgentSkillVersionPo.java', 'class AgentSkillVersionPo'),
    ('ai-event-gateway-database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/skill/po/AgentSkillAuditEntryPo.java', 'class AgentSkillAuditEntryPo'),
    ('ai-event-gateway-database-platform/src/main/resources/mybatis/postgresql/agent/AgentSkillRegistryDao.xml', 'agent_skill_versions'),
    ('ai-event-gateway-database-platform/src/main/resources/db/migration/V30__agent_skill_versioning_audit.sql', 'agent_skill_audit_entries'),
]
missing = []
for rel, token in checks:
    path = ROOT / rel
    if not path.exists() or token not in path.read_text():
        missing.append(f'{rel}: missing {token}')
if missing:
    print('\n'.join(missing), file=sys.stderr)
    sys.exit(1)
print('P9.4 skill versioning / approval / audit integration check passed.')
