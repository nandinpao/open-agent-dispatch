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
    model = "ai-event-gateway-admin-ui/lib/tasks/taskDiagnosisReadModel.ts"
    require(
        model,
        "TaskDiagnosisReadModel",
        "TaskDiagnosisPrimaryBlocker",
        "TaskPoolEligibilitySummary",
        "TaskRoutingEvidenceSummary",
        "TaskAssignmentSummary",
        "TaskDeliverySummary",
        "TaskAckResultSummary",
        "TaskDiagnosisTimelineLink",
        "CONFIGURATION_BLOCKED",
        "RUNTIME_BLOCKED",
        "DELIVERY_BLOCKED",
        "EXECUTION_BLOCKED",
        "MANUAL_ACTION_REQUIRED",
        "CORE_CONFIGURATION",
        "CORE_ROUTING",
        "NETTY_RUNTIME",
        "AGENT_RUNTIME",
        "OPERATOR",
        "buildTaskDiagnosisReadModel",
        "dispatchEvidence",
        "runtimeVerification",
        "dispatchRequests",
        "dispatchLedger",
        "callbackInboxSummary",
        "eligibleAgentsV2",
        "COMMAND_NOT_AVAILABLE",
    )
    forbid(
        model,
        "Capability Gate",
        "Service Scope Gate",
        "Assignment Profile Gate",
        "PHASE_6",
        "Phase 6",
    )

    task_detail = "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx"
    require(
        task_detail,
        "buildTaskDiagnosisReadModel",
        "TaskDiagnosisReadModelPanel",
        "taskDiagnosisReadModel",
        "Task Diagnosis Read Model",
        "Pool Eligibility Summary",
        "Evidence Chain",
        "Secondary Blockers",
        "taskDiagnosisCategoryLabel",
        "taskDiagnosisOwnerPlaneLabel",
        "dispatchEvidence: data.dispatchEvidence",
        "runtimeVerification: data.runtimeVerification",
        "dispatchRequests: data.dispatchRequests",
        "dispatchLedger: data.dispatchLedger",
        "callbackInboxSummary: data.callbackInboxSummary",
        "eligibleAgentsV2: data.eligibleAgentsV2",
    )
    forbid(
        task_detail,
        "Phase 6A Task Diagnosis Read Model",
        "PHASE_6A",
        "Capability Gate",
        "Service Scope Gate",
        "Assignment Profile Gate",
    )

    print("Phase 6A task diagnosis read model verified.")


if __name__ == "__main__":
    main()
