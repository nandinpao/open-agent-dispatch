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
        '@GetMapping("/tasks/{taskId}/issue-dedup")',
        'AdminTaskIssueDedupSummary',
        'buildIssueDedupSummary',
        'taskId + issueType + issueScope + activeStatus',
        'Update occurrenceCount and lastOccurredAt; do not create duplicate active Issue.',
        'RECOVERABLE_AUTO_RESOLVE_ON_COMPLETION',
        'GOVERNANCE_REVIEW_REQUIRED',
        'AUTO_RESOLVED',
        'REVIEW_REQUIRED',
        'Task succeeded; recoverable Issue is eligible for automatic resolution.',
    )

    endpoints = "ai-event-gateway-admin-ui/lib/api/endpoints.ts"
    require(endpoints, 'taskIssueDedup', '/admin/tasks/${encodeURIComponent(taskId)}/issue-dedup')

    api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
    require(api, 'getTaskIssueDedup', 'CoreTaskIssueDedupSummary', 'coreAdminEndpoints.taskIssueDedup(taskId)')

    types = "ai-event-gateway-admin-ui/lib/types/core.ts"
    require(types, 'CoreTaskIssueDedupSummary', 'activeIssueKey', 'occurrenceCount', 'autoResolutionPolicy', 'repeatedOccurrenceBehavior')

    model = "ai-event-gateway-admin-ui/lib/tasks/taskDiagnosisReadModel.ts"
    require(
        model,
        'TaskIssueDedupSummary',
        'TaskUnifiedTimelineStage',
        'EVENT',
        'TASK',
        'ROUTING',
        'ASSIGNMENT',
        'DELIVERY',
        'ACK',
        'RESULT',
        'RETRY',
        'MANUAL_ACTION',
        'ISSUE',
        'COMPLETION',
        'REMEDIATION_COMMAND_AUDIT',
        'buildIssueDedupSummary',
        'externalIssueDedup',
        'remediationCommandAudits',
        'taskId + issueType + issueScope + activeStatus',
        'Update occurrenceCount and lastOccurredAt; do not create duplicate active Issue.',
        '此 Task 已完成；可恢復型 Issue 應自動解決，治理型 Issue 仍需人工 review。',
    )

    hook = "ai-event-gateway-admin-ui/hooks/useTaskDetail.ts"
    require(hook, 'issueDedup', 'getTaskIssueDedup(taskId)', 'CoreTaskIssueDedupSummary')

    detail = "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx"
    require(
        detail,
        'Active Issue Dedup',
        'Unified Timeline · Event → Task → Routing → Assignment → Delivery → ACK → Result → Retry → Manual Action → Issue → Completion',
        'remediationCommandAudits',
        'setRemediationCommandAudits',
        'externalIssueDedup: data.issueDedup',
        'Dedup key',
        'Occurrences',
        '開啟外部 Issue',
    )
    forbid(detail, '人工處置 Command 尚未開放')

    print("Phase 6C issue dedup and unified timeline verified.")

if __name__ == "__main__":
    main()
