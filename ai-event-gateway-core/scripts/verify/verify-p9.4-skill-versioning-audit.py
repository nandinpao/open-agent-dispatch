#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = [
    ('agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillVersion.java', 'class AgentSkillVersion'),
    ('agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillAuditEntry.java', 'class AgentSkillAuditEntry'),
    ('agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillLifecycleStatus.java', 'PENDING_APPROVAL'),
    ('agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryRepository.java', 'listVersions'),
    ('agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'createDraftVersion'),
    ('agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'publishVersion'),
    ('agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java', 'rollbackToVersion'),
    ('control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java', '/admin/agent-skills/{skillCode}/versions'),
    ('database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/skill/po/AgentSkillVersionPo.java', 'class AgentSkillVersionPo'),
    ('database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/skill/po/AgentSkillAuditEntryPo.java', 'class AgentSkillAuditEntryPo'),
    ('database-platform/src/main/resources/mybatis/postgresql/agent/AgentSkillRegistryDao.xml', 'agent_skill_versions'),
    ('database-platform/src/main/resources/db/migration/V30__agent_skill_versioning_audit.sql', 'agent_skill_audit_entries'),
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
