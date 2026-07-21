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
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchSimulationController.java",
        '@RequestMapping("/admin/dispatch")',
        '@PostMapping("/simulate")',
        "RoutingSimulationService",
        "without creating Task, Assignment, Delivery, ACK, or Result",
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingSimulationService.java",
        "FlowResolver",
        "FlowRuleRoutingService",
        "PoolResolver",
        "RuntimeEligibilityEvaluator",
        "SelectionStrategyRegistry",
        "NO_TASK_NO_ASSIGNMENT_NO_DELIVERY_NO_ACK_NO_RESULT",
        "routingDecisionService.selectCandidates",
        "V2RoutingComparison.notApplied",
    )
    forbid(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingSimulationService.java",
        "eventIntakeApplicationService.intake",
        ".saveAndRecord(",
        "AssignmentCreator",
        "DispatchRequestDao",
        "TaskDao",
    )
    require(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchSimulationResponse.java",
        "sideEffectFree",
        "createdArtifacts",
        "candidateEvidence",
        "blockedCandidates",
        "selectedAgentId",
    )
    require(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRuntimeQuery.java",
        "getFlowId()",
        "setFlowId",
    )
    require(
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java",
        "hasFlowId",
        "f.flow_id = :flowId",
    )
    require(
        "ai-event-gateway-admin-ui/lib/api/endpoints.ts",
        "dispatchSimulation: '/admin/dispatch/simulate'",
    )
    require(
        "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts",
        "simulateDispatch",
        "CoreDispatchSimulationRequest",
        "CoreDispatchSimulationResponse",
    )
    require(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx",
        "runDispatchSimulation",
        "coreAdminApi.simulateDispatch",
        "no-side-effect Dispatch Simulation",
        "Runtime 狀態會持續變動",
        "命中 Flow",
        "命中 Rule",
        "目標 Pool",
        "候選 / 可接單",
        "預計 Agent",
        "createdArtifacts",
    )
    forbid(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx",
        "測試這個派工設定（Phase 5D）",
        "Phase 5D 將呼叫 production resolver",
    )
    print("Phase 5D no-side-effect Dispatch Simulation verified.")


if __name__ == "__main__":
    main()
