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
    checklist = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchSetupChecklist.tsx"
    require(
        checklist,
        "Beginner Journey / Setup Check",
        "設定檢查",
        "建立來源系統",
        "建立並核准 Agent",
        "建立工作池並加入 Agent",
        "建立 Source Flow 並指定 Default Pool",
        "確認 Runtime 可接單",
        "執行派工模擬",
        "送出真實測試事件",
        "不要求理解 Capability、Profile 或 Scope",
        "重新開啟設定檢查",
    )
    forbid(checklist, "Capability Gate", "Service Scope Gate", "Assignment Profile Gate")

    require(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspace.tsx",
        "DispatchSetupChecklist",
        "setupChecklistOpen",
        "latestSimulationResult",
        "latestRealTestResult",
        "onSimulationResultChange={setLatestSimulationResult}",
        "onRealTestResultChange={setLatestRealTestResult}",
        "本工作區採 Source System / Source Flow 主導設計",
    )
    forbid(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspace.tsx",
        "Phase 5E",
        "PHASE_5E",
        "Service Scope",
        "Assignment Profile",
    )

    require(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx",
        "runRealTestEvent",
        "coreAdminApi.createDispatchFlowRealTestEvent",
        "dispatch-workspace-simulation",
        "dispatch-workspace-real-test",
        "送出真實測試事件",
        "正式 Task",
        "BEGINNER_JOURNEY_REAL_TEST",
        "onSimulationResultChange?.(result)",
        "onRealTestResultChange?.(result)",
    )
    forbid(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx",
        "PHASE_5E_BEGINNER_JOURNEY",
        "Capability Gate",
        "Service Scope Gate",
        "Assignment Profile Gate",
    )

    require(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/SourceFlowMasterList.tsx",
        "空資料庫導引",
        "前往來源系統",
        "此來源尚無 Source Flow",
        "建立 Source Flow",
    )

    print("Phase 5E beginner journey and usability gate verified.")


if __name__ == "__main__":
    main()
