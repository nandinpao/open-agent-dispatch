from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = {
    'resolver service': ROOT / 'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/TaskDispatchContractResolverService.java',
    'resolve request': ROOT / 'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/TaskDispatchContractResolveRequest.java',
    'resolve result': ROOT / 'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/TaskDispatchContractResolveResult.java',
    'approved skill': ROOT / 'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentApprovedSkill.java',
    'controller': ROOT / 'control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java',
    'mybatis xml': ROOT / 'database-platform/src/main/resources/mybatis/postgresql/agent/AgentSkillRegistryDao.xml',
}
for name, path in checks.items():
    if not path.exists():
        raise SystemExit(f'Missing {name}: {path}')

controller = checks['controller'].read_text()
for token in [
    '/admin/dispatch-contracts/resolve',
    '/admin/agents/{agentId}/skills/approved',
    '/admin/agents/{agentId}/skills/sync-approved-capabilities',
    'AgentProfileUpdateCommand',
]:
    if token not in controller:
        raise SystemExit(f'Missing controller token: {token}')

repo = (ROOT / 'agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryRepository.java').read_text()
for token in ['findApprovedSkills', 'replaceApprovedSkills']:
    if token not in repo:
        raise SystemExit(f'Missing repository token: {token}')

routing = (ROOT / 'task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java').read_text()
if 'TaskDispatchContractResolverService' not in routing or 'dispatchContractResolverService.resolve' not in routing:
    raise SystemExit('RoutingDecisionService does not use TaskDispatchContractResolverService')

xml = checks['mybatis xml'].read_text()
for token in ['ApprovedSkillMap', 'findApprovedSkills', 'upsertApprovedSkill']:
    if token not in xml:
        raise SystemExit(f'Missing MyBatis token: {token}')

print('P9.3 dispatch contract resolver and approved skill sync check passed.')
