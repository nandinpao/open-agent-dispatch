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
    page = "ai-event-gateway-admin-ui/app/dispatch-flows/page.tsx"
    workspace = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspace.tsx"
    master = "ai-event-gateway-admin-ui/components/dispatch-workspace/SourceFlowMasterList.tsx"
    sections = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx"
    model = "ai-event-gateway-admin-ui/components/dispatch-workspace/dispatchWorkspaceModel.ts"

    require(page, "DispatchWorkspace", "DispatchFlowsPage")
    forbid(page, "<AgentPoolManagementConsole", "<DispatchContractBuilderConsole")

    require(
        workspace,
        "Dispatch Workspace",
        "Source System / Source Flow 主導",
        "xl:grid-cols-[minmax(280px,0.9fr)_minmax(0,2.1fr)]",
        "sourceSystem",
        "flowId",
        "router.replace(buildWorkspaceHref",
        "coreAdminApi.getSourceSystems",
        "coreAdminApi.getDispatchFlows",
        "coreAdminApi.getAgentPools",
    )

    require(
        master,
        "來源與 Source Flow",
        "先選來源系統，再選要維護的 Source Flow",
        "Default Pool",
        "此來源尚無 Source Flow",
    )

    require(
        sections,
        "Flow 基本資料",
        "預設派工",
        "特殊分類規則",
        "工作池與 Agent",
        "測試與啟用",
        "建立新工作池",
        "測試這個派工設定",
        "Agent Pool 是目前 Flow 的派工資源，不再是派工設定頁第一操作物件",
        "Capability 可在 Agent 詳細頁作為搜尋與治理參考",
    )

    require(
        model,
        "flowHealthIssues",
        "sourceHealthLabel",
        "sectionState",
        "ruleConditionSummary",
    )

    print("Phase 5B dispatch workspace shell verified.")


if __name__ == "__main__":
    main()
