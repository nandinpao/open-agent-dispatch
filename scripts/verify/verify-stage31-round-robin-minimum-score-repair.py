#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2]
service = root / 'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/cutover/GenericDispatchAuthoritativeService.java'
routing = root / 'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java'
doc = root / 'docs/PHASE31_ROUND_ROBIN_MINIMUM_SCORE_REPAIR.md'

failures = []
text = service.read_text()
if 'scoreThresholdApplies(TaskRequirementEvidence requirement)' not in text:
    failures.append('GenericDispatchAuthoritativeService must define scoreThresholdApplies(...)')
if 'strategy != GenericRoutingStrategy.ROUND_ROBIN' not in text:
    failures.append('ROUND_ROBIN must bypass the global minimum score threshold')
if 'scores.isEmpty()' not in text or 'GENERIC_SCORE_BELOW_MINIMUM' not in text:
    failures.append('empty candidate and below-threshold cases must be separated')
if 'score threshold bypassed for non-quality ranking strategy' not in text:
    failures.append('selected result should explain threshold bypass for ROUND_ROBIN')

routing_text = routing.read_text()
if 'trace.put("scoreBreakdown", candidate.scoreBreakdown())' not in routing_text:
    failures.append('RoutingDecisionService generic candidate trace must include scoreBreakdown')

doc_text = doc.read_text()
for token in ['ROUND_ROBIN', 'minimum-score', 'RETRY_WAIT', 'score=46']:
    if token not in doc_text:
        failures.append(f'documentation missing {token}')

if failures:
    print('Stage 31 round-robin minimum-score repair verification failed:')
    for failure in failures:
        print(' - ' + failure)
    sys.exit(1)
print('Stage 31 round-robin minimum-score repair contract verified.')
