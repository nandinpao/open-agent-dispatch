#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors = []

def require(path, text):
    content = (ROOT / path).read_text(encoding='utf-8')
    if text not in content:
        errors.append(f"{path}: missing {text!r}")

startup = ROOT / 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java'
startup_text = startup.read_text(encoding='utf-8')
for text in ['phase=P6_12', 'task_lifecycle_auto_assign_retry_due']:
    if text not in startup_text:
        errors.append(f"{startup.relative_to(ROOT)}: missing {text!r}")

facade = ROOT / 'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/DefaultTaskOrchestrationFacade.java'
facade_text = facade.read_text(encoding='utf-8')
for text in [
    'case QUEUED,CREATED,RETRY_WAIT->policy.createdTimeout()',
    'shouldRetryDispatchReady',
    'task_lifecycle_auto_assign_retry_due',
    'task_lifecycle_auto_assign_retry_started',
    'task_lifecycle_auto_assign_retry_completed',
]:
    if text not in facade_text:
        errors.append(f"{facade.relative_to(ROOT)}: missing {text!r}")

for forbidden in [
    'result.isAssignmentCreated()',
    'result.getSelectedAgentId()',
    'result.isDispatchRequestCreated()',
    'result.getAssignmentStatus()',
    'result.getReason()',
]:
    if forbidden in facade_text:
        errors.append(f"{facade.relative_to(ROOT)}: forbidden Java record getter {forbidden}; use record accessor methods instead")

for text in [
    'result.assignmentCreated()',
    'result.selectedAgentId()',
    'result.dispatchRequestCreated()',
    'result.assignmentStatus()',
    'result.reason()',
]:
    if text not in facade_text:
        errors.append(f"{facade.relative_to(ROOT)}: missing Java record accessor {text}")

service = ROOT / 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/lifecycle/TaskLifecycleService.java'
service_text = service.read_text(encoding='utf-8')
if 'task_lifecycle_policy_loaded' not in service_text:
    errors.append(f"{service.relative_to(ROOT)}: missing task_lifecycle_policy_loaded")

for compose in ['deploy/docker-compose.local.yml', 'deploy/docker-compose.ci.yml', 'deploy/docker-compose.release.yml']:
    c = (ROOT / compose).read_text(encoding='utf-8')
    for text in [
        'TASK_LIFECYCLE_TIMEOUT_ENABLED:',
        'TASK_LIFECYCLE_AUTO_REASSIGN_ENABLED:',
        'TASK_LIFECYCLE_SCAN_INTERVAL_MS:',
        'TASK_LIFECYCLE_CREATED_TIMEOUT:',
        'TASK_LIFECYCLE_ASSIGNED_TIMEOUT:',
        'TASK_LIFECYCLE_DISPATCHED_TIMEOUT:',
        'TASK_LIFECYCLE_RUNNING_TIMEOUT:',
        'TASK_LIFECYCLE_MAX_REASSIGNMENTS:',
    ]:
        if text not in c:
            errors.append(f"{compose}: missing {text}")

for env in ['deploy/env/.env.local.example', 'deploy/env/.env.local.ci']:
    e = (ROOT / env).read_text(encoding='utf-8')
    for text in [
        'TASK_LIFECYCLE_CREATED_TIMEOUT=45s',
        'TASK_LIFECYCLE_ASSIGNED_TIMEOUT=45s',
        'TASK_LIFECYCLE_DISPATCHED_TIMEOUT=45s',
        'TASK_LIFECYCLE_RUNNING_TIMEOUT=60s',
    ]:
        if text not in e:
            errors.append(f"{env}: missing {text}")

require('docs/P6_12_QUEUED_TASK_AUTO_ASSIGN_RETRY_AND_LIFECYCLE_ENV_FIX/README.md', 'P6.12 Queued Task Auto-Assign Retry')

if errors:
    print('P6.12 verification failed:')
    for error in errors:
        print(' -', error)
    sys.exit(1)

print('P6.12 queued task auto-assign retry and lifecycle env verification passed.')
