#!/usr/bin/env python3
"""Stage 5 real Event / Task diagnosis contract report.

This report is static and dry-run friendly. It proves the repository exposes
Stage 5's intended standard operator path before live Core/Netty/Agent
verification is executed in a Java 25 + Docker environment.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".ci-output" / "stage5-real-event-task-diagnosis"

CONTROLLER = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java"
CORE_TEST = ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/DispatchFlowControllerRealTestEventTest.java"
TASK_DECISION = ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java"
API = ROOT / "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
ENDPOINTS = ROOT / "ai-event-gateway-admin-ui/lib/api/endpoints.ts"
FLOW_UI = ROOT / "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx"
LIFECYCLE = ROOT / "ai-event-gateway-admin-ui/lib/tasks/dispatchLifecycle.ts"
TASK_UI = ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx"
UI_TEST = ROOT / "ai-event-gateway-admin-ui/tests/stage5-real-event-task-diagnosis.test.ts"
PACKAGE = ROOT / "ai-event-gateway-admin-ui/package.json"
DOCS = [
    ROOT / "docs/STAGE5_REAL_EVENT_TASK_DIAGNOSIS/README.md",
    ROOT / "docs/STAGE5_REAL_EVENT_TASK_DIAGNOSIS/test-matrix.md",
    ROOT / "docs/STAGE5_REAL_EVENT_TASK_DIAGNOSIS/validation-report.md",
    ROOT / "docs/STAGE5_REAL_EVENT_TASK_DIAGNOSIS/next-stage.md",
    ROOT / "docs/STAGE5_REAL_EVENT_TASK_DIAGNOSIS/changed-files.md",
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
            findings.append(Finding("ERROR", category, rel(path), f"Forbidden Stage 5 standard-flow token: {token}"))


def segment_between(text: str, start: str, end: str) -> str:
    if start not in text:
        return ""
    segment = text[text.index(start):]
    if end in segment:
        return segment[:segment.index(end)]
    return segment


def main() -> None:
    findings: list[Finding] = []

    require_tokens(findings, CONTROLLER, [
        '@PostMapping("/{flowId}/test-event")',
        "eventIntakeApplicationService.intake(request)",
        "openDispatchRealTestEvent",
        "requireTenantMatch",
    ], "REAL_EVENT_ENDPOINT")
    reject_tokens(findings, CONTROLLER, [
        "DispatchContractTestTaskResponse",
        "dispatchReadinessEvaluationService.evaluate",
        "dryRunDispatchFlow",
    ], "NO_SIMULATOR_ENDPOINT")

    require_tokens(findings, CORE_TEST, [
        "createsRealEventFromPersistedActiveFlow",
        "verify(intake).intake",
        "ACTIVE",
        "flowId",
    ], "CORE_REAL_EVENT_TDD")

    task_decision_text = read(TASK_DECISION)
    require_tokens(findings, TASK_DECISION, [
        "NO_ACTIVE_FLOW_RULE",
        "Flow-selected approved Agent",
    ], "CANONICAL_RUNTIME_BLOCKER")
    if "NO_ACTIVE_FLOW_RULE" in task_decision_text:
        gate_segment = segment_between(task_decision_text, "NO_ACTIVE_FLOW_RULE", "task.setLifecycleReason(blocked)")
        for token in ["SOURCE_DEFAULT", "Agent Source Coverage", "Service Scope", "Assignment Profile"]:
            if token in gate_segment:
                findings.append(Finding("ERROR", "CANONICAL_RUNTIME_BLOCKER", rel(TASK_DECISION), f"No-flow standard operator message still references legacy path: {token}"))

    require_tokens(findings, API, [
        "createDispatchFlowRealTestEvent",
        "dispatchFlowRealTestEvent",
    ], "ADMIN_API_REAL_EVENT")
    require_tokens(findings, ENDPOINTS, [
        "dispatchFlowRealTestEvent",
        "/admin/dispatch-flows/",
        "/test-event",
    ], "ADMIN_API_REAL_EVENT")

    require_tokens(findings, FLOW_UI, [
        "發送真實測試事件",
        "createDispatchFlowRealTestEvent",
        "查看真實 Task 與時間線",
    ], "FLOW_UI_REAL_EVENT_ACTION")
    reject_tokens(findings, FLOW_UI, [
        "dryRunDispatchFlow(",
        "createDispatchReadinessTestEvent(",
        "DispatchReadinessWizard",
    ], "FLOW_UI_NO_READINESS")

    require_tokens(findings, LIFECYCLE, [
        "deriveTaskDispatchDiagnosis",
        "buildStandardDispatchTimeline",
        "NO_ACTIVE_FLOW_RULE",
        "REQUIRED_CAPABILITY_MISSING",
        "AGENT_OFFLINE",
        "AGENT_NO_CAPACITY",
        "NO_ELIGIBLE_AGENT",
        "DELIVERY_FAILED",
        "CALLBACK_FAILED",
    ], "CANONICAL_TASK_DIAGNOSIS")

    require_tokens(findings, TASK_UI, [
        "TaskPrimaryDiagnosisPanel",
        "StandardDispatchTimelinePanel",
        "Event → Task → Flow → Agent → Result",
        "Support Debug",
        "Legacy Scope、Profile 與 Readiness repair 不再是一般操作入口",
    ], "TASK_DETAIL_STANDARD_DIAGNOSIS")
    reject_tokens(findings, TASK_UI, [
        "runTaskDispatchReadiness",
        "repairDispatchContract",
        'import { TaskDispatchContractTracePanel',
        'import { TaskDispatchEvidenceTimelinePanel',
        'import { DispatchTroubleshootingWizard',
    ], "TASK_DETAIL_NO_STANDARD_READINESS_REPAIR")

    require_tokens(findings, UI_TEST, [
        "builds the same eight-step Event-to-Result timeline",
        "posts to the persisted Flow real-test endpoint with the selected tenant",
        "NO_ACTIVE_FLOW_RULE",
        "REQUIRED_CAPABILITY_MISSING",
        "AGENT_OFFLINE",
    ], "ADMIN_UI_STAGE5_TDD")

    require_tokens(findings, PACKAGE, [
        "verify:stage5-real-event-task",
        "test:stage5-real-event-task",
        "stage5:real-event-task",
    ], "ADMIN_UI_STAGE5_SCRIPTS")

    for doc in DOCS:
        if not doc.exists():
            findings.append(Finding("ERROR", "STAGE5_DOCUMENTATION", rel(doc), "Missing Stage 5 documentation file"))

    status = "PASS" if not findings else "FAIL"
    OUT.mkdir(parents=True, exist_ok=True)
    report = {
        "stage": "Stage 5 - Real Event and Task Diagnosis",
        "status": status,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "summary": {
            "findingCount": len(findings),
            "errorCount": sum(1 for finding in findings if finding.severity == "ERROR"),
            "contract": "Dispatch Flow test action must create a formal Event through EventIntakeApplicationService and Task Detail must show one canonical operator diagnosis plus the standard Event-to-Result timeline.",
        },
        "canonicalBlockers": [
            "NO_ACTIVE_FLOW_RULE",
            "REQUIRED_CAPABILITY_MISSING",
            "AGENT_OFFLINE",
            "AGENT_NO_CAPACITY",
            "NO_ELIGIBLE_AGENT",
            "DELIVERY_FAILED",
            "CALLBACK_FAILED",
        ],
        "findings": [asdict(finding) for finding in findings],
    }
    (OUT / "contract-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    lines = [
        "# Stage 5 Real Event and Task Diagnosis Contract Report",
        "",
        f"Status: **{status}**",
        f"Generated at: `{report['generatedAt']}`",
        "",
        "## Contract",
        "",
        "Dispatch Flow test action must use the formal Event intake path and create a real Task. Task Detail must show one canonical operator diagnosis and the standard timeline:",
        "",
        "```text",
        "Event → Task → Flow → Agent → Assignment → Delivery → ACK → Result",
        "```",
        "",
        "Standard operator remediation must not point users to Service Scope, Assignment Profile, Source Default, or Test Dispatch Readiness.",
        "",
        "## Canonical blockers",
        "",
    ]
    lines.extend([f"- `{code}`" for code in report["canonicalBlockers"]])
    lines.extend(["", "## Findings", ""])
    if findings:
        for finding in findings:
            lines.append(f"- **{finding.severity}** `{finding.category}` `{finding.file}` — {finding.detail}")
    else:
        lines.append("No Stage 5 static contract findings.")
    lines.extend([
        "",
        "## Release-grade gate still required",
        "",
        "This report is not a replacement for Java 25 + Docker live verification. Run:",
        "",
        "```bash",
        "make test-stage5-core",
        "make up-agent",
        "CORE_URL=http://127.0.0.1:18080 make characterize-stage1-strict",
        "```",
        "",
    ])
    (OUT / "contract-report.md").write_text("\n".join(lines), encoding="utf-8")

    print(f"Stage 5 real Event / Task diagnosis contract report: {status} findings={len(findings)}")
    if findings:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
