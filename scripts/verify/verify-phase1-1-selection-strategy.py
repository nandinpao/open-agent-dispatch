#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

checks: list[tuple[Path, str, str]] = [
    (Path('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java'),
     'SUPPORTED_POOL_SELECTION_STRATEGIES = Set.of("LOWEST_LOAD", "WEIGHTED_SCORE", "MANUAL_ONLY")',
     'RoutingDecisionService must declare the current supported Pool selection strategies.'),
    (Path('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java'),
     'MANUAL_ASSIGNMENT_REQUIRED',
     'MANUAL_ONLY Pool routing must produce an explicit manual assignment reason.'),
    (Path('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java'),
     'poolWeightedEffectiveScore',
     'WEIGHTED_SCORE must emit weighted effective score evidence.'),
    (Path('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java'),
     'round(baseScore * 0.8 + normalizedMemberWeight * 20)',
     'WEIGHTED_SCORE formula must be documented in runtime evidence.'),
    (Path('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java'),
     'normalizeSupportedPoolSelectionStrategy',
     'Agent Pool management must normalize unsupported strategies.'),
    (Path('ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx'),
     "SUPPORTED_SELECTION_STRATEGIES = ['LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY']",
     'Agent Pool UI must expose only supported current strategies.'),
    (Path('ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx'),
     'ROUND_ROBIN / LOCAL_FIRST 尚未成為正式 Runtime 策略',
     'Agent Pool UI must explain hidden unsupported strategies.'),
    (Path('docs/current/development/phase1-1-selection-strategy.md'),
     'weightedEffectiveScore = round(baseScore * 0.8 + normalizedMemberWeight * 20)',
     'Phase 1-1 strategy contract document must describe the weighted formula.'),
]

for rel, token, message in checks:
    text = (ROOT / rel).read_text(encoding='utf-8')
    if token not in text:
        print(f'FAIL: {message}\n  Missing token in {rel}: {token}', file=sys.stderr)
        sys.exit(1)

ui_text = (ROOT / 'ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx').read_text(encoding='utf-8')
for forbidden in ['<option value="ROUND_ROBIN">', '<option value="LOCAL_FIRST">']:
    if forbidden in ui_text:
        print(f'FAIL: unsupported strategy remains selectable in Agent Pool UI: {forbidden}', file=sys.stderr)
        sys.exit(1)

routing_text = (ROOT / 'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java').read_text(encoding='utf-8')
if 'isManualOnlyPool(candidatePool)' not in routing_text or 'RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED' not in routing_text:
    print('FAIL: MANUAL_ONLY must be intercepted before automatic selection.', file=sys.stderr)
    sys.exit(1)

print('Phase 1-1 selection strategy contract verified.')
