#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors = []

def require(path, token):
    p = ROOT / path
    if not p.exists():
        errors.append(f"missing file: {path}")
        return
    text = p.read_text(errors='ignore')
    if token not in text:
        errors.append(f"{path}: missing token {token!r}")

for path, tokens in {
    'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java': [
        'phase=P6_12', 'task_lifecycle_scan_started', 'callback_replay_duplicate_accepted'
    ],
    'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/lifecycle/ScheduledTaskLifecycle.java': [
        'task_lifecycle_scan_started', 'task_lifecycle_scan_completed'
    ],
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/DefaultTaskOrchestrationFacade.java': [
        'task_lifecycle_scan_candidates', 'task_lifecycle_auto_reassign_due', 'task_lifecycle_timeout_due', 'timeoutFor(task,policy)'
    ],
    'ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackService.java': [
        'callback_replay_duplicate_accepted', 'PREVIOUS_TERMINAL_CALLBACK_ALREADY_ACCEPTED', 'isTerminalCallback'
    ],
    'ai-event-gateway-core/control-plane-app/src/main/resources/logback-spring.xml': [
        'com.opensocket.aievent.core.lifecycle'
    ],
    'deploy/env/.env.local.example': [
        'TASK_LIFECYCLE_SCAN_INTERVAL_MS=5000', 'TASK_LIFECYCLE_DISPATCHED_TIMEOUT=45s', 'TASK_LIFECYCLE_RUNNING_TIMEOUT=60s'
    ],
    'deploy/env/.env.local.ci': [
        'TASK_LIFECYCLE_SCAN_INTERVAL_MS=5000', 'TASK_LIFECYCLE_DISPATCHED_TIMEOUT=45s', 'TASK_LIFECYCLE_RUNNING_TIMEOUT=60s'
    ],
    'docs/P6_11_LIFECYCLE_REASSIGN_AND_CALLBACK_REPLAY_HARDENING/README.md': [
        'CALLBACK_REPLAY_MISMATCH', 'task_lifecycle_auto_reassign_due', 'phase=P6_12'
    ],
}.items():
    for token in tokens:
        require(path, token)

if errors:
    print('P6.11 verification failed:')
    for error in errors:
        print(' -', error)
    sys.exit(1)
print('P6.11 verification passed: lifecycle auto-reassign visibility/tuning and terminal callback replay duplicate handling are present.')
