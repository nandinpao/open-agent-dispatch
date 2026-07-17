#!/usr/bin/env python3
"""Verify Phase E-P2 Redmine / Issue Tracking management hooks."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED = {
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/api/AdapterActionController.java": [
        "/issue-tracking/redmine/diagnostics",
        "/issue-tracking/redmine/test-connection",
        "/issue-tracking/redmine/projects",
        "/issue-tracking/redmine/trackers",
        "/issue-tracking/redmine/test-issue",
        "X-Redmine-API-Key",
        "RedmineTestIssueRequest",
        "priorityMapping",
        "resolveRedminePriorityId",
        "priority_id",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/AdapterActionService.java": [
        "eventMessage",
        "issueTitle(task, incident, callback)",
        "message",
        "severityLabel",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/AgentIssueHistoryFormatter.java": [
        "- Severity:",
        "- Source system:",
        "- Message:",
        "- Occurrence count:",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/RedmineIssueVendorExecutor.java": [
        "formatIssueUrlTemplate",
        "{issueId}",
        "%s",
        "redminePriorityId",
        "issue_priorities",
        "priority_id",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/AdapterActionExecutionProperties.java": [
        "priorityCritical",
        "priorityHigh",
        "priorityMedium",
        "priorityLow",
    ],
    "ai-event-gateway-core/control-plane-app/src/main/resources/application.yml": [
        "REDMINE_EXECUTOR_PRIORITY_CRITICAL",
        "REDMINE_EXECUTOR_PRIORITY_HIGH",
        "REDMINE_EXECUTOR_PRIORITY_MEDIUM",
        "REDMINE_EXECUTOR_PRIORITY_LOW",
    ],
    "ai-event-gateway-admin-ui/lib/api/endpoints.ts": [
        "issueTrackingRedmineDiagnostics",
        "issueTrackingRedmineTestConnection",
        "issueTrackingRedmineProjects",
        "issueTrackingRedmineTrackers",
        "issueTrackingRedmineTestIssue",
        "adapterActionExecutePending",
        "adapterActionExecutorAudit",
    ],
    "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts": [
        "getRedmineIssueTrackingDiagnostics",
        "testRedmineIssueTrackingConnection",
        "createRedmineTestIssue",
        "executePendingAdapterActions",
        "getAdapterExecutorAudit",
    ],
    "ai-event-gateway-admin-ui/lib/types/core.ts": [
        "priorityMapping",
        "severity?: string",
        "message?: string",
        "priorityId?: string",
    ],
    "ai-event-gateway-admin-ui/lib/issue-tracking/issueTrackingManagement.ts": [
        "redmineManagementDecision",
        "adapterActionQueueSummary",
        "redmineCoreEnvTemplate",
        "REDMINE_EXECUTOR_ENABLED=true",
        "REDMINE_EXECUTOR_API_KEY=<redmine-api-key>",
        "REDMINE_EXECUTOR_PRIORITY_CRITICAL=CRITICAL",
    ],
    "ai-event-gateway-admin-ui/components/issue-tracking/IssueTrackingManagementConsole.tsx": [
        "P2 · Issue Tracking 管理化",
        "P2 hotfix · Redmine 設定來源",
        "Redmine Host / API Key 要設定在 Core 環境變數",
        "deploy/env/.env.local",
        "REDMINE_EXECUTOR_BASE_URL",
        "REDMINE_EXECUTOR_API_KEY",
        "測試 Redmine 連線",
        "送出測試 Issue",
        "Action Queue / Result",
        "issue 標題 / 內容 / 優先權",
        "Execute Pending",
        "API Key 只留在 Core 環境變數",
        "Subject Override",
        "Severity / Redmine Priority",
    ],
    "ai-event-gateway-admin-ui/app/settings/issue-tracking/page.tsx": [
        "Issue Tracking / Redmine 管理",
        "IssueTrackingManagementConsole",
    ],
    "ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts": [
        "Issue Tracking",
        "/settings/issue-tracking",
    ],
    "ai-event-gateway-admin-ui/tests/issue-tracking-management.test.ts": [
        "Phase E-P2 marks Redmine management blocked",
        "adapterActionQueueSummary",
        "redmineConnectionLabel",
    ],
    "docs/PHASE_E_P2_ISSUE_TRACKING_MANAGEMENT.md": [
        "Redmine / Issue Tracking 管理化",
        "Redmine Host / API Key 設定來源",
        "deploy/env/.env.local",
        "test-connection",
        "test-issue",
        "Action Queue / Result",
        "issue 標題 / 內容 / 優先權",
    ],
    "deploy/env/.env.local.example": [
        "Issue Tracking / Redmine local integration",
        "REDMINE_EXECUTOR_BASE_URL=http://baofire.com:8700",
        "REDMINE_EXECUTOR_API_KEY=REPLACE_WITH_REDMINE_API_KEY",
        "REDMINE_EXECUTOR_PROJECT_ID=redmine",
        "REDMINE_EXECUTOR_TRACKER_ID=3",
        "REDMINE_EXECUTOR_PRIORITY_MEDIUM=MIDDLE",
    ],
    "deploy/env/.env.baofire.local.example": [
        "Issue Tracking / Redmine local integration",
        "REDMINE_EXECUTOR_BASE_URL=http://baofire.com:8700",
        "REDMINE_EXECUTOR_API_KEY=REPLACE_WITH_REDMINE_API_KEY",
        "REDMINE_EXECUTOR_PRIORITY_CRITICAL=CRITICAL",
    ],
    "deploy/env/.env.release.example": [
        "Optional Issue Tracking / Redmine integration",
        "REDMINE_EXECUTOR_BASE_URL=REPLACE_WITH_REDMINE_BASE_URL",
        "REDMINE_EXECUTOR_API_KEY=REPLACE_WITH_REDMINE_API_KEY",
        "REDMINE_EXECUTOR_PRIORITY_CRITICAL=REPLACE_WITH_REDMINE_CRITICAL_PRIORITY_NAME_OR_ID",
    ],
    "deploy/docker-compose.local.yml": [
        "Issue Tracking / Redmine local integration",
        "REDMINE_EXECUTOR_BASE_URL: ${REDMINE_EXECUTOR_BASE_URL:-}",
        "REDMINE_EXECUTOR_API_KEY: ${REDMINE_EXECUTOR_API_KEY:-}",
        "REDMINE_EXECUTOR_PRIORITY_CRITICAL: ${REDMINE_EXECUTOR_PRIORITY_CRITICAL:-CRITICAL}",
    ],
    "deploy/docker-compose.release.yml": [
        "Issue Tracking / Redmine integration",
        "REDMINE_EXECUTOR_BASE_URL: ${REDMINE_EXECUTOR_BASE_URL:-}",
        "REDMINE_EXECUTOR_API_KEY: ${REDMINE_EXECUTOR_API_KEY:-}",
        "REDMINE_EXECUTOR_PRIORITY_CRITICAL: ${REDMINE_EXECUTOR_PRIORITY_CRITICAL:-CRITICAL}",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx": [
        "TaskIssueLink",
        "issue.issueUrl",
        "Severity {display.severity.label}",
        "display.severity.isHighImpact",
    ],
    "ai-event-gateway-admin-ui/lib/tasks/taskWorkbench.ts": [
        "TaskWorkbenchSeverity",
        "severityDisplay",
        "eventSeverity",
        "incidentSeverity",
    ],
    "ai-event-gateway-admin-ui/lib/tasks/issueTrackingBridge.ts": [
        "mergeIssueTrackingIntoTasks",
        "actionMatchesTask",
        "issueUrl",
    ],
    "ai-event-gateway-admin-ui/hooks/useTasks.ts": [
        "mergeIssueTrackingIntoTasks",
        "getAdapterActions(300)",
        "tasksWithIssueBridge",
    ],
    "ai-event-gateway-admin-ui/tests/task-workbench-display.test.ts": [
        "surfaces severity from event payload",
        "keeps Redmine issue link data available",
    ],
    "scripts/ci/local-smoke.sh": [
        "SMOKE_ACCEPT_CORE_STATUS_WHEN_HEALTH_CONVERGING",
        "Core API ready; aggregate actuator health still converging",
        "/api/core/status HTTP status",
    ],
    "Makefile": [
        "deploy/env/.env.local",
        "deploy/env/.env.local.example",
    ],
    "scripts/ci/local-cd.sh": [
        "deploy/env/.env.local",
        "deploy/env/.env.local.example",
    ],
    "scripts/local-compose-up.sh": [
        "deploy/env/.env.local",
        "deploy/env/.env.local.example",
    ],
}



def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def main() -> int:
    for rel, markers in REQUIRED.items():
        path = ROOT / rel
        if not path.is_file():
            fail(f"Missing required file: {rel}")
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                fail(f"{rel} does not contain required marker: {marker}")
    print("Phase E-P2 Issue Tracking management verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
