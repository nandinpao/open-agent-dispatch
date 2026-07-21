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
    workspace = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspace.tsx"
    sections = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx"
    master = "ai-event-gateway-admin-ui/components/dispatch-workspace/SourceFlowMasterList.tsx"

    require(
        workspace,
        "handleCreateFlow",
        "coreAdminApi.createDispatchFlow",
        "DISPATCH_WORKSPACE_FLOW_EDITOR",
        "建立 Source Flow",
        "本工作區採 Source System / Source Flow 主導設計",
    )

    require(
        sections,
        "PoolEditorDrawer",
        "RuleEditorDrawer",
        "handleDefaultPoolChange",
        "coreAdminApi.updateDispatchFlow",
        "coreAdminApi.createAgentPool",
        "coreAdminApi.updateAgentPool",
        "Default Agent Pool",
        "建立新工作池",
        "Rule target Pool",
        "新增規則",
        "編輯特殊分類規則",
        "管理 Pool 成員",
        "DISPATCH_WORKSPACE_POOL_MEMBER_PRESERVING",
        "RESOURCE_VERSION_CONFLICT",
        "Metadata 保留",
        "weight:",
        "priority:",
        "memberStatus",
    )

    require(
        master,
        "此來源尚無 Source Flow",
        "建立 Source Flow",
    )

    forbid(
        sections,
        "建立新工作池（Phase 5C）",
        "查看工作池 Drawer（Phase 5C）",
        "編輯（Phase 5C）",
        "儲存並啟用（Phase 5C）",
    )

    print("Phase 5C Flow/Rule/Pool integrated editor verified.")


if __name__ == "__main__":
    main()
