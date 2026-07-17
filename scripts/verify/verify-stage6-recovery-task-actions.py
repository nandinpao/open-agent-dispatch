#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise AssertionError(f'{label}: missing {token!r}')


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise AssertionError(f'{label}: forbidden {token!r}')


def main() -> int:
    repository = read('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskRepository.java')
    require(repository, 'suspendDispatchUntilConfigurationChange', 'configuration suspension contract')
    require(repository, 'wakeConfigurationBlockedTasks', 'configuration wake contract')
    require(repository, 'WAITING_CONFIGURATION:', 'configuration blocker marker')

    mapper = read('ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml')
    claim = mapper[mapper.index('<select id="claimDispatchRecoveryDue"'):mapper.index('</select>', mapper.index('<select id="claimDispatchRecoveryDue"'))]
    require(claim, 't.next_dispatch_attempt_at is not null', 'recovery scanner due-time requirement')
    forbid(claim, "FLOW_RULE_REQUIRED_BLOCKED", 'recovery scanner must not reclaim configuration-blocked tasks')
    forbid(claim, 'dispatch_policies p', 'recovery scanner must not infer configuration changes by polling')
    require(mapper, '<update id="wakeConfigurationBlockedTasks">', 'PostgreSQL configuration wake')


    assignment = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignmentService.java')
    require(assignment, 'configurationBlockerCode', 'configuration blocker classification')
    require(assignment, 'suspendUntilConfigurationChange', 'configuration suspension')
    require(assignment, 'NO_ACTIVE_FLOW_RULE', 'missing Flow blocker')
    require(assignment, 'REQUIRED_CAPABILITY_MISSING', 'missing Capability blocker')
    forbid(assignment, 'Profile / Qualification / Certification', 'standard delayed retry wording')

    flow = read('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java')
    require(flow, 'wakeConfigurationBlockedTasks', 'Flow save wakes blocked tasks')
    capability = read('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java')
    require(capability, 'Agent Capability approved', 'Capability approval wakes blocked tasks')
    require(capability, 'Agent Capability resumed', 'Capability resume wakes blocked tasks')

    failure_queue = read('ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/TaskFailureQueueService.java')
    require(failure_queue, 'retryReason.equals(task.getDispatchRetryReason())', 'manual retry idempotency')

    for path in [
        'ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx',
        'ai-event-gateway-admin-ui/components/tasks/TaskFailureQueuePanel.tsx',
        'ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx',
    ]:
        source = read(path)
        require(source, 'TaskActionDialog', f'governed Task action UI {path}')
        forbid(source, 'window.prompt', f'native prompt {path}')
        forbid(source, 'window.confirm', f'native confirm {path}')
        forbid(source, 'window.alert', f'native alert {path}')

    dialog = read('ai-event-gateway-admin-ui/components/tasks/TaskActionDialog.tsx')
    require(dialog, 'validateTaskActionDialogInput', 'Task action validation')
    require(dialog, 'minimumReasonLength', 'auditable reason length')
    require(dialog, 'requiredPhrase', 'high-risk confirmation phrase')

    java_test = read('ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/task/InMemoryTaskRepositoryFlowRecoveryTest.java')
    require(java_test, 'configurationBlockedTaskIsNotReclaimedUntilConfigurationChanges', 'Stage 6 recovery TDD')
    postgres_test = read('ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage6ConfigurationBlockedRecoveryContainerTest.java')
    require(postgres_test, 'configurationBlockedTaskIsNotClaimedUntilMatchingConfigurationWakesIt', 'Stage 6 PostgreSQL recovery TDD')
    require(postgres_test, 'claimDispatchRecoveryDue', 'Stage 6 PostgreSQL scanner claim')
    require(postgres_test, 'wakeConfigurationBlockedTasks', 'Stage 6 PostgreSQL configuration wake')
    ui_test = read('ai-event-gateway-admin-ui/tests/stage6-recovery-task-actions.test.ts')
    require(ui_test, 'removes native browser dialogs', 'Stage 6 UI TDD')

    print('Stage 6 recovery and governed Task action contract verified.')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f'ERROR: {exc}', file=sys.stderr)
        raise SystemExit(1)
