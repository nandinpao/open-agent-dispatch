#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
required = [
    'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillDependencyEdge.java',
    'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillDependencyGraph.java',
    'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRemediationAction.java',
    'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRemediationProposal.java',
    'database-platform/src/main/resources/db/migration/V33__agent_skill_dependency_edges.sql',
]
missing = [p for p in required if not (ROOT / p).exists()]
if missing:
    print('Missing P9.7 files:', missing)
    sys.exit(1)
service = (ROOT / 'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java').read_text()
for token in ['dependencyGraph(', 'replaceDependencyEdges(', 'proposeAgentRemediation(', 'proposeFleetRemediation(', 'ADD_REQUIRED_DEPENDENCY_SKILL', 'REVIEW_CONFLICTING_SKILL_APPROVALS']:
    if token not in service:
        print('Missing service token:', token)
        sys.exit(1)
controller = (ROOT / 'control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java').read_text()
for token in ['/dependency-graph', '/dependencies', '/drift/remediation-proposals', '/skills/remediation-proposal']:
    if token not in controller:
        print('Missing controller endpoint token:', token)
        sys.exit(1)
xml = ROOT / 'database-platform/src/main/resources/mybatis/postgresql/agent/AgentSkillRegistryDao.xml'
ET.parse(xml)
print('P9.7 skill dependency graph / drift remediation integration check passed.')
