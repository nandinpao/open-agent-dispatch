#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise SystemExit(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")

def require(relative: str, *tokens: str) -> None:
    content = read(relative)
    for token in tokens:
        if token not in content:
            raise SystemExit(f"{relative}: missing required token: {token}")

def forbid(relative: str, *tokens: str) -> None:
    content = read(relative)
    for token in tokens:
        if token in content:
            raise SystemExit(f"{relative}: forbidden token remains: {token}")

def main() -> None:
    controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java"
    require(
        controller,
        '@PostMapping("/tasks/{taskId}/commands")',
        'AdminTaskRemediationCommandType',
        'REEVALUATE_ROUTING',
        'ASSIGN_AGENT',
        'CHANGE_POOL',
        'MOVE_TO_MANUAL_QUEUE',
        'RETRY_DELIVERY',
        'RETRY_TASK',
        'CANCEL_TASK',
        'IGNORE_TASK',
        'expectedTaskVersion',
        'idempotencyKey',
        'RESOURCE_VERSION_CONFLICT',
        'allowedTaskRemediationCommands',
        'beforeState',
        'afterState',
        'Original automatic routing evidence is preserved',
        'assignToSpecificAgent',
        'dispatchRequestService.retry',
        'taskOrchestrationFacade.saveExecutionState',
        'taskRemediationIdempotencyCache',
        'MANUAL_OVERRIDE',
    )

    endpoints = "ai-event-gateway-admin-ui/lib/api/endpoints.ts"
    require(endpoints, 'taskCommands', '/admin/tasks/${encodeURIComponent(taskId)}/commands')

    api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
    require(api, 'runTaskRemediationCommand', 'CoreTaskRemediationCommandRequest', 'CoreTaskRemediationCommandResult', 'coreAdminEndpoints.taskCommands(taskId)')

    types = "ai-event-gateway-admin-ui/lib/types/core.ts"
    require(types, 'CoreTaskRemediationCommandType', 'CoreTaskRemediationCommandRequest', 'CoreTaskCommandAudit', 'CoreTaskRemediationCommandResult', 'allowedCommandsAfter', 'idempotentReplay')

    model = "ai-event-gateway-admin-ui/lib/tasks/taskRemediationCommands.ts"
    require(
        model,
        'deriveAllowedTaskRemediationCommands',
        'buildTaskRemediationCommandRequest',
        'taskRuntimeVersion',
        'expectedTaskVersion',
        'idempotencyKey',
        'targetAgentId',
        'targetPoolId',
        'CONFIRM_CANCEL_TASK',
        'CONFIRM_IGNORE_TASK',
    )
    forbid(model, 'Capability Gate', 'Service Scope Gate', 'Assignment Profile Gate')

    dialog = "ai-event-gateway-admin-ui/components/tasks/TaskActionDialog.tsx"
    require(dialog, 'allowTargetPool', 'targetPoolId', '目標工作池 Pool ID')

    detail = "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx"
    require(
        detail,
        'TaskRemediationActionsPanel',
        'deriveAllowedTaskRemediationCommands',
        'buildTaskRemediationCommandRequest',
        'pendingRemediationCommand',
        'runTaskRemediationCommand',
        'Actions are state-filtered below',
        '僅顯示合法操作',
        'before/after audit',
        '原始自動派工 Evidence 不會被覆蓋',
        'allowTargetPool={pendingRemediationCommand?.requiredPayload === "targetPoolId"}',
    )
    forbid(detail, '人工處置 Command 尚未開放')

    diagnosis = "ai-event-gateway-admin-ui/lib/tasks/taskDiagnosisReadModel.ts"
    require(diagnosis, 'Task command model', '查看可執行人工處置')
    forbid(diagnosis, '人工處置 Command 尚未開放')

    print("Phase 6B task remediation command model verified.")

if __name__ == "__main__":
    main()
