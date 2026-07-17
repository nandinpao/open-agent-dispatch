#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[2]
checks = [
    ('task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', 'evaluateSkillAware'),
    ('task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', 'skillPenalty'),
    ('task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java', 'skillAwareEnabled'),
    ('agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentDispatchSkillEvaluationService.java', 'AgentDispatchSkillEvaluationService'),
    ('task-orchestration/src/test/java/com/opensocket/aievent/core/routing/SkillAwareRoutingDecisionServiceTest.java', 'shouldPreferAgentThatPassesSkillRegistryContractWhenEnabled'),
    ('docs/P9_2_SKILL_AWARE_DISPATCH.md', 'routing.skill-aware-enabled'),
]
for rel, needle in checks:
    path = root / rel
    if not path.exists():
        raise SystemExit(f'Missing required file: {rel}')
    text = path.read_text(encoding='utf-8')
    if needle not in text:
        raise SystemExit(f'Expected {rel} to include {needle}')
print('P9.2 skill-aware dispatch integration check passed.')
