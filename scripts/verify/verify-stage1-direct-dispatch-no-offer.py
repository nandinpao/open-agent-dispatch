#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SKIP_DIRS = {'.git', 'target', 'node_modules', '.ci-output', 'dist', 'build'}
FORBIDDEN = [
    'Task' + 'Offer',
    'task-' + 'offers',
    r'task\.' + 'offer',
    'TASK_' + 'OFFER',
    'OFFER' + 'ING',
    'OFFER' + 'ED',
    'OFFER_' + 'ACCEPTED',
    'OFFER_' + 'REJECTED',
    'OFFER_' + 'TIMEOUT',
    'offer' + '_id',
    'offer' + 'Id',
    'create' + 'Offer' + 'IfEligibleAsAssignmentDecision',
    'shouldUse' + 'Offer' + 'First',
    'is' + 'Offer' + 'FirstAssignmentEnabled',
    'Task' + 'Offer' + 'Properties',
    'OFFER_' + 'FIRST',
    'offer' + '-first',
]
BINARY_SUFFIXES = {'.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.jar', '.class', '.zip', '.gz', '.tar'}


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        raise SystemExit(f"missing required Phase 1 file: {rel}")
    return path.read_text(encoding='utf-8')


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise SystemExit(f"missing {label}: {token}")


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise SystemExit(f"forbidden {label}: {token}")

violations: list[str] = []
for path in ROOT.rglob('*'):
    if not path.is_file():
        continue
    if any(part in SKIP_DIRS for part in path.parts):
        continue
    if path.suffix.lower() in BINARY_SUFFIXES:
        continue
    try:
        text = path.read_text(encoding='utf-8')
    except UnicodeDecodeError:
        continue
    rel = path.relative_to(ROOT)
    # This verifier constructs forbidden strings dynamically below; do not self-match on its own literal pattern list.
    if rel.as_posix() == 'scripts/verify/verify-stage1-direct-dispatch-no-offer.py':
        continue
    for pattern in FORBIDDEN:
        if re.search(pattern, text, re.IGNORECASE if pattern == ('offer' + '-first') else 0):
            violations.append(f"{rel}: forbidden pattern {pattern}")

if violations:
    raise SystemExit('Stage 1 direct dispatch gate failed:\n' + '\n'.join(violations[:200]))

for rel in [
    'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/' + 'Task' + 'Offer' + 'Controller.java',
    'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/' + 'offer/' + 'Task' + 'Offer' + '.java',
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/' + 'offer/' + 'Task' + 'Offer' + 'OrchestrationService.java',
    'openclaw/src/protocol/' + 'Task' + 'Offer' + '.ts',
    'openclaw/src/runtime/' + 'Task' + 'Offer' + 'Router.ts',
]:
    if (ROOT / rel).exists():
        raise SystemExit(f"removed two-step dispatch artifact still exists: {rel}")

task_decision = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java')
facade = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/DefaultTaskOrchestrationFacade.java')
action_gov_path = ROOT / 'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionGovernanceService.java'
action_gov = action_gov_path.read_text(encoding='utf-8') if action_gov_path.exists() else ''
status = read('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskStatus.java')
assignment = read('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignment.java')
openclaw_client = read('openclaw/src/netty/OpenSocketClient.ts')
openclaw_runtime = read('openclaw/src/reliability/TaskReliabilityRuntime.ts')

require(task_decision, 'taskAssignmentService.assignIfPossible(saved)', 'TaskDecisionService direct assignment path')
require(facade, 'taskAssignmentService.assignIfPossible(task)', 'DefaultTaskOrchestrationFacade direct assignment path')
if action_gov:
    require(action_gov, 'assignmentService.assignToSpecificAgent', 'ActionGovernanceService direct assignment path')
forbid(status, 'OFFER', 'offer task statuses')
forbid(assignment, 'offer', 'assignment offer correlation')
forbid(openclaw_client, 'Task' + 'Offer', 'OpenClaw removed two-step protocol import')
forbid(openclaw_runtime, 'offers', 'OpenClaw offer router')

migration = read('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql')
forbid(migration, 'task_offers', 'task_offers table')
forbid(migration, 'offer' + '_id', 'removed two-step schema column')
require(migration, 'Dispatch Flow Direct Delivery schema', 'direct dispatch clean baseline marker')

print('Stage 1 direct dispatch no-offer contract verified.')
