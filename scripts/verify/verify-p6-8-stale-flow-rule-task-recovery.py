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
    mapper = read('ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml')
    claim = mapper[mapper.index('<select id="claimDispatchRecoveryDue"'):mapper.index('</select>', mapper.index('<select id="claimDispatchRecoveryDue"'))]
    require(claim, 't.next_dispatch_attempt_at is not null', 'P6.8 superseded due-time scanner')
    forbid(claim, 'FLOW_RULE_REQUIRED_BLOCKED', 'P6.8 superseded polling repair')
    forbid(claim, 'dispatch_policies p', 'P6.8 superseded polling repair')
    require(mapper, '<update id="wakeConfigurationBlockedTasks">', 'configuration change wake')
    require(mapper, "dispatch_retry_reason like 'WAITING_CONFIGURATION:%'", 'configuration blocked marker')

    memory = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/InMemoryTaskRepository.java')
    require(memory, 'suspendDispatchUntilConfigurationChange', 'in-memory configuration suspension')
    require(memory, 'wakeConfigurationBlockedTasks', 'in-memory configuration wake')
    forbid(memory, 'isStaleFlowRuleBlockedTask', 'old polling stale repair')

    flow = read('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java')
    require(flow, 'dispatch_flow_configuration_tasks_awakened', 'Flow save wake diagnostics')

    facade = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/DefaultTaskOrchestrationFacade.java')
    for marker in [
        'task_dispatch_recovery_claimed',
        'task_dispatch_recovery_recovered',
        'task_dispatch_recovery_deferred',
        'task_dispatch_recovery_skipped',
    ]:
        require(facade, marker, 'recovery diagnostics')

    test = read('ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/task/InMemoryTaskRepositoryFlowRecoveryTest.java')
    require(test, 'configurationBlockedTaskIsNotReclaimedUntilConfigurationChanges', 'event-driven recovery TDD')

    print('P6.8 superseded configuration-change recovery verification passed')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f'P6.8 verification failed: {exc}', file=sys.stderr)
        raise SystemExit(1)
