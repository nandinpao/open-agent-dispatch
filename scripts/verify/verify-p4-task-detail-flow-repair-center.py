#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
TASK_DETAIL = ROOT / 'ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx'
WIZARD = ROOT / 'ai-event-gateway-admin-ui/components/tasks/DispatchTroubleshootingWizard.tsx'
HOOK = ROOT / 'ai-event-gateway-admin-ui/hooks/useTaskDetail.ts'
CONTROLLER = ROOT / 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java'
ACTIONS = ROOT / 'ai-event-gateway-admin-ui/lib/dispatch-readiness/dispatchOperatorActions.ts'
DOC = ROOT / 'docs/P4_TASK_DETAIL_FLOW_REPAIR_CENTER/README.md'

errors: list[str] = []

def read(path: Path) -> str:
    return path.read_text(encoding='utf-8') if path.exists() else ''

task_detail = read(TASK_DETAIL)
wizard = read(WIZARD)
hook = read(HOOK)
controller = read(CONTROLLER)
actions = read(ACTIONS)
doc = read(DOC)

for token in [
    'Flow 修復中心',
    'Event → Flow Rule → requestedSkill → Agent Assignment → Runtime Delivery → RESULT',
    'MISSING_FLOW_RULE',
    'NO_REQUESTED_SKILL',
    'NO_FLOW_AGENT_ASSIGNMENT',
    'AGENT_SKILL_GRANT_MISSING',
    'NO_DISPATCH_REQUEST',
    'NO_RESULT_CALLBACK',
    'Create / Fix Rule',
    'Add Skill / Grant',
    'Assign Agent',
    'Open Agent Runtime',
]:
    if token not in task_detail:
        errors.append(f'Missing Task Detail P4 token: {token}')

for token in [
    'CoreTaskCaseTimelineView',
    'coreAdminApi.getTaskCaseTimeline(taskId)',
    'caseTimelineError',
]:
    if token not in hook:
        errors.append(f'Missing useTaskDetail case timeline token: {token}')

for token in [
    'P4_TASK_DETAIL_FLOW_REPAIR_CENTER',
    'MISSING_FLOW_RULE',
    'NO_REQUESTED_SKILL',
    'NO_FLOW_AGENT_ASSIGNMENT',
    'FLOW_AGENT_ASSIGNMENT',
    'RUNTIME_DELIVERY',
    'AGENT_RESULT',
    'ISSUE_UPDATE',
]:
    if token not in controller:
        errors.append(f'Missing backend case timeline token: {token}')

for token in [
    'Flow Rule Match',
    'Requested Skill',
    'Flow Agent Assignment',
    'Agent Skill Grant',
    'Runtime Delivery / RESULT',
]:
    if token not in wizard:
        errors.append(f'Missing troubleshooting wizard Flow token: {token}')

for token in [
    'Create / Fix Dispatch Flow Rule',
    'Add requestedSkill',
    'Assign Agent on Flow',
    'Grant requestedSkill',
]:
    if token not in actions:
        errors.append(f'Missing operator action Flow token: {token}')

for token in [
    'Task Detail Flow Repair Center',
    'Event Received',
    'Flow Rule Match',
    'Agent RESULT',
    'Acceptance',
]:
    if token not in doc:
        errors.append(f'Missing P4 doc token: {token}')

# The main repair panel must not direct beginner operators to old fragmented pages.
panel_start = task_detail.find('function TaskCaseTimelineRepairPanel')
panel_end = task_detail.find('function PayloadPanel', panel_start)
panel_text = task_detail[panel_start:panel_end] if panel_start != -1 and panel_end != -1 else task_detail
for legacy_phrase in ['Assignment Profile', 'Service Scope', 'Task Definition', '/supply-profiles', '/settings/dispatch-task-definitions']:
    if legacy_phrase in panel_text:
        errors.append(f'Legacy phrase/link appears in P4 repair panel: {legacy_phrase}')

if errors:
    print('P4 Task Detail Flow Repair Center verification failed:')
    for error in errors:
        print(f' - {error}')
    sys.exit(1)

print('P4 Task Detail Flow Repair Center verification passed.')
