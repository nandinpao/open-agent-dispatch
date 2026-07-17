#!/usr/bin/env python3
"""Stage 6 recovery and governed Task action contract report.

This report is static and dry-run friendly. It validates that Stage 6 keeps
configuration-blocked Tasks out of time-based recovery loops, wakes them only
through authoritative configuration mutations or manual retry, and routes risky
operator actions through auditable governed dialogs rather than native browser
prompts.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".ci-output" / "stage6-recovery-task-actions"

TASK_REPOSITORY = ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskRepository.java"
TASK_MAPPER = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml"
TASK_ASSIGNMENT = ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignmentService.java"
FLOW_SERVICE = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
AGENT_ASSIGNMENT_CONTROLLER = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java"
FAILURE_QUEUE_SERVICE = ROOT / "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/TaskFailureQueueService.java"

TASK_DETAIL = ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx"
TASK_FAILURE_QUEUE_PANEL = ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskFailureQueuePanel.tsx"
TASK_TABLE = ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx"
TASK_ACTION_DIALOG = ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskActionDialog.tsx"
UI_TEST = ROOT / "ai-event-gateway-admin-ui/tests/stage6-recovery-task-actions.test.ts"
PACKAGE = ROOT / "ai-event-gateway-admin-ui/package.json"

JAVA_RECOVERY_TEST = ROOT / "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/task/InMemoryTaskRepositoryFlowRecoveryTest.java"
JAVA_FAILURE_QUEUE_TEST = ROOT / "ai-event-gateway-core/execution-control/src/test/java/com/opensocket/aievent/core/dispatch/TaskFailureQueueServiceTest.java"
JAVA_POSTGRES_TEST = ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage6ConfigurationBlockedRecoveryContainerTest.java"

DOCS = [
    ROOT / "docs/STAGE6_RECOVERY_TASK_ACTIONS/README.md",
    ROOT / "docs/STAGE6_RECOVERY_TASK_ACTIONS/test-matrix.md",
    ROOT / "docs/STAGE6_RECOVERY_TASK_ACTIONS/validation-report.md",
    ROOT / "docs/STAGE6_RECOVERY_TASK_ACTIONS/next-stage.md",
    ROOT / "docs/STAGE6_RECOVERY_TASK_ACTIONS/changed-files.md",
]


@dataclass
class Finding:
    severity: str
    category: str
    file: str
    detail: str


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def read(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8")


def require_tokens(findings: list[Finding], path: Path, tokens: Iterable[str], category: str) -> None:
    text = read(path)
    if not text:
        findings.append(Finding("ERROR", category, rel(path), "Required file is missing or empty"))
        return
    for token in tokens:
        if token not in text:
            findings.append(Finding("ERROR", category, rel(path), f"Missing contract token: {token}"))


def reject_tokens(findings: list[Finding], path: Path, tokens: Iterable[str], category: str) -> None:
    text = read(path)
    if not text:
        findings.append(Finding("ERROR", category, rel(path), "Required file is missing or empty"))
        return
    for token in tokens:
        if token in text:
            findings.append(Finding("ERROR", category, rel(path), f"Forbidden Stage 6 standard-flow token: {token}"))


def extract_between(text: str, start: str, end: str) -> str:
    if start not in text:
        return ""
    segment = text[text.index(start):]
    if end in segment:
        return segment[:segment.index(end)]
    return segment


def main() -> None:
    findings: list[Finding] = []

    require_tokens(findings, TASK_REPOSITORY, [
        "suspendDispatchUntilConfigurationChange",
        "wakeConfigurationBlockedTasks",
        "WAITING_CONFIGURATION:",
    ], "CONFIGURATION_BLOCKED_REPOSITORY_CONTRACT")

    mapper_text = read(TASK_MAPPER)
    require_tokens(findings, TASK_MAPPER, [
        '<select id="claimDispatchRecoveryDue"',
        "t.next_dispatch_attempt_at is not null",
        '<update id="wakeConfigurationBlockedTasks"',
        "WAITING_CONFIGURATION:",
    ], "POSTGRES_RECOVERY_SCANNER_CONTRACT")
    claim_segment = extract_between(mapper_text, '<select id="claimDispatchRecoveryDue"', '</select>')
    for token in ["dispatch_policies p", "FLOW_RULE_REQUIRED_BLOCKED", "service_scope", "assignment_profile"]:
        if token in claim_segment:
            findings.append(Finding("ERROR", "POSTGRES_RECOVERY_SCANNER_CONTRACT", rel(TASK_MAPPER), f"Scanner should not poll configuration or legacy dependency: {token}"))

        "configurationBlockerCode",
        "suspendDispatchUntilConfigurationChange",
    require_tokens(findings, TASK_ASSIGNMENT, [
        "configurationBlockerCode",
        "suspendUntilConfigurationChange",
        "NO_ACTIVE_FLOW_RULE",
        "REQUIRED_CAPABILITY_MISSING",
    ], "ASSIGNMENT_CONFIGURATION_SUSPENSION")
    reject_tokens(findings, TASK_ASSIGNMENT, [
        "Profile / Qualification / Certification",
    ], "NO_LEGACY_RETRY_GUIDANCE")

    require_tokens(findings, FLOW_SERVICE, [
        "wakeConfigurationBlockedTasks",
    ], "FLOW_MUTATION_WAKES_BLOCKED_TASKS")
    require_tokens(findings, AGENT_ASSIGNMENT_CONTROLLER, [
        "Agent Capability approved",
        "Agent Capability resumed",
        "wakeConfigurationBlockedTasks",
    ], "CAPABILITY_MUTATION_WAKES_BLOCKED_TASKS")

    require_tokens(findings, FAILURE_QUEUE_SERVICE, [
        "retryReason.equals(task.getDispatchRetryReason())",
    ], "MANUAL_RETRY_IDEMPOTENCY")

    for ui_file in [TASK_DETAIL, TASK_FAILURE_QUEUE_PANEL, TASK_TABLE]:
        require_tokens(findings, ui_file, ["TaskActionDialog"], "GOVERNED_TASK_ACTION_UI")
        reject_tokens(findings, ui_file, ["window.prompt", "window.confirm", "window.alert"], "NO_NATIVE_BROWSER_DIALOGS")

    require_tokens(findings, TASK_ACTION_DIALOG, [
        "validateTaskActionDialogInput",
        "minimumReasonLength",
        "requiredPhrase",
        "reasonRequired",
    ], "TASK_ACTION_DIALOG_VALIDATION")

    require_tokens(findings, UI_TEST, [
        "requires an auditable reason",
        "requires the exact high-risk confirmation phrase",
        "removes native browser dialogs",
    ], "ADMIN_UI_STAGE6_TDD")
    require_tokens(findings, PACKAGE, [
        "verify:stage6-recovery-task-actions",
        "test:stage6-recovery-task-actions",
        "stage6:recovery-task-actions",
    ], "ADMIN_UI_STAGE6_SCRIPTS")

    require_tokens(findings, JAVA_RECOVERY_TEST, [
        "configurationBlockedTaskIsNotReclaimedUntilConfigurationChanges",
    ], "CORE_RECOVERY_TDD")
    require_tokens(findings, JAVA_FAILURE_QUEUE_TEST, [
        "manualRetryIsIdempotent",
    ], "CORE_MANUAL_RETRY_TDD")
    require_tokens(findings, JAVA_POSTGRES_TEST, [
        "configurationBlockedTaskIsNotClaimedUntilMatchingConfigurationWakesIt",
        "claimDispatchRecoveryDue",
        "wakeConfigurationBlockedTasks",
    ], "POSTGRES_RECOVERY_TDD")

    for doc in DOCS:
        if not doc.exists():
            findings.append(Finding("ERROR", "STAGE6_DOCUMENTATION", rel(doc), "Missing Stage 6 documentation file"))

    status = "PASS" if not findings else "FAIL"
    OUT.mkdir(parents=True, exist_ok=True)
    report = {
        "stage": "Stage 6 - Recovery and Governed Task Actions",
        "status": status,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "summary": {
            "findingCount": len(findings),
            "errorCount": sum(1 for finding in findings if finding.severity == "ERROR"),
            "contract": "Configuration-blocked Tasks must not be reclaimed by time-based recovery; Flow/Capability configuration changes or manual retry wake them once; governed Task actions must be auditable and idempotent.",
        },
        "configurationBlockers": [
            "NO_ACTIVE_FLOW_RULE",
            "REQUIRED_CAPABILITY_MISSING",
            "NO_FLOW_SELECTED_AGENT",
        ],
        "runtimeRetryConditions": [
            "AGENT_OFFLINE",
            "AGENT_NO_CAPACITY",
            "DELIVERY_FAILED",
            "CALLBACK_FAILED",
        ],
        "gates": [
            "make verify-stage6-recovery-task-actions",
            "make characterize-stage6-dry-run",
            "make test-stage6-admin-ui",
            "make test-stage6-core",
        ],
        "findings": [asdict(finding) for finding in findings],
    }
    (OUT / "contract-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    lines = [
        "# Stage 6 Recovery and Governed Task Actions Contract Report",
        "",
        f"Generated at: `{report['generatedAt']}`",
        "",
        f"Status: **{status}**",
        "",
        "## Summary",
        "",
        f"- Findings: {report['summary']['findingCount']}",
        f"- Errors: {report['summary']['errorCount']}",
        "- Contract: configuration-blocked Tasks are suspended until configuration changes; manual retry is idempotent; risky Task actions use auditable dialogs.",
        "",
        "## Configuration-blocked Task contract",
        "",
        "- Scanner claims only rows with `next_dispatch_attempt_at is not null` and due.",
        "- Configuration blockers are stored as `WAITING_CONFIGURATION:<blockerCode>:<reason>`.",
        "- Flow aggregate saves and Agent Capability approval/resume wake matching blocked Tasks once.",
        "- Manual retry with the same reason is idempotent and must not create duplicate assignment work.",
        "",
        "## Governed Task action contract",
        "",
        "- Retry, cancel, reassign, recovery, escalate, dead-letter, and restore actions use `TaskActionDialog`.",
        "- Native `window.prompt`, `window.confirm`, and `window.alert` are forbidden in standard Task pages.",
        "- Auditable reason and exact confirmation phrase are enforced for high-risk actions.",
        "",
    ]
    if findings:
        lines.extend(["## Findings", ""])
        for finding in findings:
            lines.append(f"- **{finding.severity}** `{finding.category}` `{finding.file}` — {finding.detail}")
        lines.append("")
    else:
        lines.extend(["## Findings", "", "No findings.", ""])
    (OUT / "contract-report.md").write_text("\n".join(lines), encoding="utf-8")

    print(f"Stage 6 recovery/task action contract report: {status} findings={len(findings)}")
    if findings:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
