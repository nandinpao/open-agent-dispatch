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
    task_workbench = "ai-event-gateway-admin-ui/lib/tasks/taskWorkbench.ts"
    require(task_workbench, "未提供來源系統", "Source Flow、目標 Agent Pool、Pool 成員與 Runtime Eligibility")
    forbid(task_workbench, "function inferSystem", "return 'MES';", "return 'ERP';", "return 'OpenDispatch';")

    beginner = "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts"
    require(
        beginner,
        "建立來源系統",
        "建立並核准 Agent",
        "建立工作池並加入 Agent",
        "建立 Source Flow 並指定預設工作池",
        "執行派工檢查或模擬",
        "Capability 只作描述與搜尋參考，不是 Current 派工必要條件",
    )
    forbid(
        beginner,
        "Confirm capability policy and Dispatch Flow Agent selection",
        "Approve Agent service Flow Agent approval",
        "Task Requirement must map to Agent Dispatch Flow Agent assignment",
    )

    require(
        "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx",
        "WorkspaceTenantSelector",
        "md:hidden",
    )
    require(
        "ai-event-gateway-admin-ui/components/common/AdminUiModeNotice.tsx",
        "切換模式不會增加權限",
        "後端 RBAC",
    )
    require(
        "ai-event-gateway-admin-ui/app/layout.tsx",
        'lang="zh-Hant"',
        "OpenDispatch 管理中心",
    )
    require(
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx",
        "DEFAULT_PROCESSING_POOL",
        "預設處理池",
    )
    require(
        "ai-event-gateway-admin-ui/app/dispatch-flows/page.tsx",
        "DispatchWorkspace",
        "DispatchFlowsPage",
    )
    forbid(
        "ai-event-gateway-admin-ui/app/dispatch-flows/page.tsx",
        "<AgentPoolManagementConsole",
        "<DispatchContractBuilderConsole",
    )
    require(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspace.tsx",
        "Source System / Source Flow 主導",
        "sourceSystem",
        "flowId",
    )
    require(
        "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx",
        "Flow 基本資料",
        "預設派工",
        "特殊分類規則",
        "工作池與 Agent",
        "測試與啟用",
    )
    forbid(
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx",
        "ERP_TRIAGE_POOL",
        "ERP 一線分類池",
    )

    for relative in (
        "ai-event-gateway-admin-ui/components/tasks/TaskDispatchEvidenceTimelinePanel.tsx",
        "ai-event-gateway-admin-ui/components/tasks/TaskDispatchContractTracePanel.tsx",
        "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
        "ai-event-gateway-admin-ui/components/dashboard/DualPlaneDashboard.tsx",
        "ai-event-gateway-admin-ui/components/cluster/ClusterNodeDetailView.tsx",
    ):
        forbid(relative, "Phase 8", "P10.7", "P10.4", "R8 正式修復", "P3.9")

    print("Current UI semantics verified.")


if __name__ == "__main__":
    main()
