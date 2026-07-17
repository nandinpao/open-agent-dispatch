#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise AssertionError(f"{label}: missing {token!r}")


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise AssertionError(f"{label}: forbidden {token!r}")


def main() -> int:
    controller = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java")
    require(controller, '@PostMapping("/{flowId}/test-event")', "real test event endpoint")
    require(controller, "eventIntakeApplicationService.intake(request)", "formal event intake")
    require(controller, "openDispatchRealTestEvent", "real event evidence")
    forbid(controller, "DispatchContractTestTaskResponse", "real test event must not use simulator task")

    task_decision = read("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java")
    require(task_decision, "NO_ACTIVE_FLOW_RULE", "canonical no-flow reason")
    require(task_decision, "Flow-selected approved Agent", "canonical no-flow remediation")
    gate_segment = task_decision[task_decision.index("NO_ACTIVE_FLOW_RULE"):task_decision.index("task.setLifecycleReason(blocked)")]
    forbid(gate_segment, "SOURCE_DEFAULT", "standard no-flow message")
    forbid(gate_segment, "Agent Source Coverage", "standard no-flow message")

    api = read("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts")
    require(api, "createDispatchFlowRealTestEvent", "Admin UI real test API")
    require(api, "dispatchFlowRealTestEvent", "Admin UI real test endpoint")

    flow_ui = read("ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx")
    require(flow_ui, "發送真實測試事件", "Flow real test action")
    require(flow_ui, "createDispatchFlowRealTestEvent", "Flow uses formal endpoint")
    require(flow_ui, "查看真實 Task 與時間線", "Flow links to real Task")
    forbid(flow_ui, "dryRunDispatchFlow(", "standard Flow action")
    forbid(flow_ui, "createDispatchReadinessTestEvent(", "standard Flow action")

    lifecycle = read("ai-event-gateway-admin-ui/lib/tasks/dispatchLifecycle.ts")
    for token in [
        "deriveTaskDispatchDiagnosis",
        "buildStandardDispatchTimeline",
        "NO_ACTIVE_FLOW_RULE",
        "REQUIRED_CAPABILITY_MISSING",
        "AGENT_OFFLINE",
        "NO_ELIGIBLE_AGENT",
    ]:
        require(lifecycle, token, "canonical Task diagnosis")

    task_ui = read("ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx")
    require(task_ui, "TaskPrimaryDiagnosisPanel", "single primary diagnosis")
    require(task_ui, "StandardDispatchTimelinePanel", "standard Task timeline")
    require(task_ui, "Event → Task → Flow → Agent → Result", "standard lifecycle wording")
    forbid(task_ui, 'import { TaskDispatchContractTracePanel', "standard Task UI")
    forbid(task_ui, 'import { TaskDispatchEvidenceTimelinePanel', "standard Task UI")
    forbid(task_ui, 'import { DispatchTroubleshootingWizard', "standard Task UI")
    forbid(task_ui, "runTaskDispatchReadiness", "standard Task UI")
    forbid(task_ui, "repairDispatchContract", "standard Task UI")

    test = read("ai-event-gateway-admin-ui/tests/stage5-real-event-task-diagnosis.test.ts")
    require(test, "builds the same eight-step Event-to-Result timeline", "Stage 5 UI TDD")
    java_test = read("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/DispatchFlowControllerRealTestEventTest.java")
    require(java_test, "createsRealEventFromPersistedActiveFlow", "Stage 5 Core TDD")
    require(java_test, "verify(intake).intake", "Stage 5 Core TDD")

    print("Stage 5 real Event / Task diagnosis contract verified.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
