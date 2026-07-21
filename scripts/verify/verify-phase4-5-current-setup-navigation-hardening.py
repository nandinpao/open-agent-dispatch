#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"Missing required file: {path}")
    return p.read_text(encoding="utf-8")


def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        raise AssertionError(f"Expected token not found in {path}: {token}")


def forbid(path: str, token: str) -> None:
    text = read(path)
    if token in text:
        raise AssertionError(f"Forbidden token found in {path}: {token}")


def main() -> None:
    subprocess.run([sys.executable, str(ROOT / "scripts/verify/verify-phase4-4-admin-ui-legacy-surface-labeling.py")], check=True)

    sidebar = "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx"
    nav = "ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts"
    dispatch_console = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx"
    source_console = "ai-event-gateway-admin-ui/components/source-systems/SourceSystemConsole.tsx"
    agent_detail = "ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx"
    task_trace = "ai-event-gateway-admin-ui/components/tasks/TaskDispatchContractTracePanel.tsx"
    task_table = "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx"
    doc = "docs/archive/phase-series/current-history/development/phase4-5-current-setup-navigation-hardening.md"
    changelog = "docs/archive/phase-series/current-history/phase4-5-change-log.md"
    modified = "docs/archive/phase-series/current-history/phase4-5-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (sidebar, nav, dispatch_console, source_console, agent_detail, task_trace, task_table, doc, changelog, modified, readme, makefile):
        read(path)

    # Main navigation must make the Current setup chain explicit.
    require(sidebar, "Current setup path")
    require(sidebar, "來源系統 → 派工設定 → Source Flow → Agent Pool → Pool Member Agent")

    require(nav, "Current setup navigation")
    require(nav, "Source System -> Dispatch Setup -> Source Flow -> Agent Pool -> Pool Member Agent")
    require(nav, "label: '派工設定'")
    require(nav, "Source Flow、預設 Agent Pool、Rule override 與 Pool Member Agent")
    require(nav, "加入 Pool 請回到派工設定")

    # Dispatch setup page must orient new operators before listing flows.
    require(dispatch_console, "Dispatch Setup")
    require(dispatch_console, "派工設定")
    require(dispatch_console, "來源系統 → Source Flow → Agent Pool → Pool Member Agent")
    require(dispatch_console, "建立 Source Flow")
    require(dispatch_console, "Current setup step {step}")
    require(dispatch_console, "Source Flow 清單")
    require(dispatch_console, "尚無 Source Flow")

    # Source System and Agent Detail must route users into the setup workspace.
    require(source_console, "到「派工設定」建立 Source Flow，選擇預設 Agent Pool，再把 Agent 加入 Pool")
    require(source_console, "預設 Agent Pool、Rule override 與 Pool Member Agent")
    require(agent_detail, "到派工設定加入 Agent Pool")
    require(agent_detail, "開啟派工設定：Source Flow / Agent Pool")
    require(agent_detail, "Current setup path：來源系統 → Source Flow → Agent Pool → Pool Member Agent")

    # Task blocked guidance must prefer Source Flow / Pool / Pool Member fixes.
    for token in (
        "SOURCE_FLOW_HAS_NO_DEFAULT_POOL",
        "RULE_TARGET_POOL_NOT_FOUND",
        "POOL_HAS_NO_ACTIVE_MEMBER",
        "POOL_AGENT_RUNTIME_NOT_FOUND",
        "POOL_AGENT_OFFLINE",
        "POOL_AGENT_CAPACITY_FULL",
        "POOL_AGENT_BACKOFF",
        "NO_ELIGIBLE_AGENT_IN_POOL",
        "Set Default Pool",
        "Fix Rule Target Pool",
        "Review Pool Members",
        "Current setup 不直接選 Flow Agent",
        "Capability 是 reference-only / diagnostic-only",
        "Source Flow -> Agent Pool -> Pool Member Agent 標準鏈",
    ):
        require(task_trace, token)

    require(task_trace, "Capability 與 legacy Flow Agent 只作 reference / diagnostic")
    require(task_table, "Source Flow、Agent Pool 與 Pool Member Agent 設定")
    require(task_table, "Capability 只作 reference / diagnostics")

    # Documentation and Make target.
    for token in (
        "Phase 4-5：Current Setup Navigation Hardening",
        "來源系統",
        "派工設定",
        "Source Flow",
        "Agent Pool",
        "Pool Member Agent",
        "No routing behavior changed",
    ):
        require(doc, token)
    require(changelog, "Phase 4-5")
    require(modified, "TaskDispatchContractTracePanel.tsx")
    # Phase 8A removes historical phase links from docs/current/README.md.
    # Keep this historical verifier focused on the Phase 4-5 artifact and Make target.
    require("docs/archive/historical-asset-governance.md", "Phase 8B")
    require(makefile, "verify-phase4-5-current-setup-navigation-hardening")
    require(makefile, "verify-phase4-4-admin-ui-legacy-surface-labeling")

    # Phase 4-5 must not introduce schema migrations or API deprecation changes.
    migration_names = [p.name for p in (ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration").glob("V*__*.sql")]
    if any(name.startswith("V7__") for name in migration_names):
        raise AssertionError("Phase 4-5 must not introduce a V7 Flyway migration")

    print("Phase 4-5 current setup navigation hardening contract verified.")


if __name__ == "__main__":
    main()
