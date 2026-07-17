#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[2]
checks = [
    ('ai-event-gateway-core-task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', 'evaluateSkillAware'),
    ('ai-event-gateway-core-task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', 'skillPenalty'),
    ('ai-event-gateway-core-task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java', 'skillAwareEnabled'),
    ('ai-event-gateway-core-agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentDispatchSkillEvaluationService.java', 'AgentDispatchSkillEvaluationService'),
    ('ai-event-gateway-core-task-orchestration/src/test/java/com/opensocket/aievent/core/routing/SkillAwareRoutingDecisionServiceTest.java', 'shouldPreferAgentThatPassesSkillRegistryContractWhenEnabled'),
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
