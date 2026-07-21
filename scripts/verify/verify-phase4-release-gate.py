#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
ROUTING_ROOT = ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing"


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


def require_file(path: str) -> None:
    if not (ROOT / path).is_file():
        raise AssertionError(f"Missing required file: {path}")


def require_dir(path: str) -> None:
    if not (ROOT / path).is_dir():
        raise AssertionError(f"Missing required directory: {path}")


def java_files_under(relative_dir: str) -> list[Path]:
    directory = ROOT / relative_dir
    require_dir(relative_dir)
    files = sorted(directory.rglob("*.java"))
    if not files:
        raise AssertionError(f"Current routing package has no Java files: {relative_dir}")
    return files


def assert_current_routing_boundary() -> None:
    current_package_dirs = (
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/eligibility",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/pool",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/flow",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/evidence",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/scoring",
    )
    forbidden_tokens = (
        "routing.legacy",
        "routing.governance",
        "GenericAuthorityBridge",
        "GenericDispatchAuthoritativeService",
        "DispatchCutoverService",
        "DispatchRequirementResolver",
        "GenericDispatchRequirementResolver",
        "AgentProfileEligibilityEvaluator",
        "CapabilityEligibilityEvaluator",
        "ServiceScope",
        "AssignmentProfile",
        "OperationProfile",
    )
    for directory in current_package_dirs:
        for path in java_files_under(directory):
            rel = path.relative_to(ROOT).as_posix()
            text = path.read_text(encoding="utf-8")
            for token in forbidden_tokens:
                if token in text:
                    raise AssertionError(f"Current routing package boundary violation: {rel} contains {token}")


def assert_bridge_access_is_restricted() -> None:
    allowed = {
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingOrchestrator.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/legacy/GenericAuthorityBridge.java",
    }
    for path in sorted(ROUTING_ROOT.rglob("*.java")):
        rel = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        if "GenericAuthorityBridge" in text and rel not in allowed:
            raise AssertionError(f"GenericAuthorityBridge may only be referenced by boundary owners; found in {rel}")


def assert_no_phase4_schema_migration() -> None:
    migration_dir = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"
    migration_names = [p.name for p in migration_dir.glob("V*__*.sql")]
    if any(name.startswith("V7__") for name in migration_names):
        raise AssertionError("Phase 4 must not introduce a V7 Flyway migration")


def assert_legacy_api_deprecation_headers() -> None:
    interceptor = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/legacy/LegacyApiDeprecationHeadersInterceptor.java"
    config = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/legacy/LegacyApiDeprecationHeadersWebConfig.java"

    for token in (
        'response.setHeader("Deprecation", "true")',
        'response.setHeader("Link", "<" + route.replacementPath() + ">; rel=\\"successor-version\\"")',
        'response.setHeader("X-OpenDispatch-Legacy-Category", route.legacyCategory())',
        'response.setHeader("X-OpenDispatch-Legacy-Status", DEPRECATION_STATUS)',
        'response.setHeader("X-OpenDispatch-Replacement", route.replacementDescription())',
        'response.setHeader("X-OpenDispatch-Current-Model", CURRENT_MODEL)',
        "SOURCE_FLOW_AGENT_POOL",
        "DEPRECATED_LEGACY_ROUTING_API",
    ):
        require(interceptor, token)

    # Capability Registry APIs were promoted to CURRENT_SUPPORT in Phase 9A.
    # They intentionally no longer receive legacy deprecation headers.
    for route in (
        "/admin/dispatch-contracts/**",
        "/admin/dispatch-contract/**",
        "/admin/dispatch-policies/**",
        "/admin/dispatch-governance/cutover/**",
        "/admin/agents/*/dispatch-eligibility",
        "/admin/tasks/*/dispatch-requirements",
        "/admin/tasks/*/eligible-agents",
        "/admin/tasks/*/eligible-agents-v2",
        "/admin/enforce/legacy-final-report",
    ):
        require(interceptor, route)
        require(config, route)


def assert_admin_ui_labeling_and_navigation() -> None:
    capability_selector = "ai-event-gateway-admin-ui/components/agents/CapabilityCardSelector.tsx"
    agent_detail = "ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx"
    evidence = "ai-event-gateway-admin-ui/components/dispatch-evidence/DispatchAssignmentEvidencePanel.tsx"
    failure = "ai-event-gateway-admin-ui/components/trace/FailureAnalysisPanel.tsx"
    core_api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
    core_types = "ai-event-gateway-admin-ui/lib/types/core.ts"
    sidebar = "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx"
    nav = "ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts"
    dispatch_console = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx"
    source_console = "ai-event-gateway-admin-ui/components/source-systems/SourceSystemConsole.tsx"
    task_trace = "ai-event-gateway-admin-ui/components/tasks/TaskDispatchContractTracePanel.tsx"
    task_table = "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx"

    for path in (
        capability_selector,
        agent_detail,
        evidence,
        failure,
        core_api,
        core_types,
        sidebar,
        nav,
        dispatch_console,
        source_console,
        task_trace,
        task_table,
    ):
        require_file(path)

    # Legacy/capability surfaces must be labeled as reference/diagnostic, not Current setup.
    for token in (
        "Reference-only capability catalog",
        "Source Flow -> Agent Pool -> Pool Member Agent",
    ):
        require(capability_selector, token)
    for token in (
        "Reference-only capability labels",
        "Reference-only / diagnostic-only section",
        "特殊能力（Reference-only）",
        "Current setup path：來源系統 → Source Flow → Agent Pool → Pool Member Agent",
    ):
        require(agent_detail, token)
    for token in (
        "Capability evidence: diagnostic-only",
        "Legacy/diagnostic requirement",
        "Reference-only capabilities",
    ):
        require(evidence, token)
    require(failure, "Capability 目前是 reference-only / diagnostic-only 線索")
    require(core_api, "reference-only / diagnostic-only for Current setup")
    require(core_api, "Legacy dispatch-contract resolver")
    require(core_types, "diagnostic-only")
    require(core_types, "not the Current setup API")

    # Main navigation must guide operators to the Current setup path.
    require(sidebar, "Current setup path")
    require(sidebar, "來源系統 → 派工設定 → Source Flow → Agent Pool → Pool Member Agent")
    require(nav, "Current setup navigation")
    require(nav, "Source System -> Dispatch Setup -> Source Flow -> Agent Pool -> Pool Member Agent")
    require(nav, "label: '派工設定'")
    require(dispatch_console, "唯一的 Current setup 入口")
    require(dispatch_console, "來源系統 → Source Flow → Agent Pool → Pool Member Agent")
    require(dispatch_console, "建立 Source Flow")
    require(source_console, "到「派工設定」建立 Source Flow，選擇預設 Agent Pool，再把 Agent 加入 Pool")
    require(agent_detail, "開啟派工設定：Source Flow / Agent Pool")

    # Task blocked actions must prefer Current model remediation.
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
        "Source Flow -> Agent Pool -> Pool Member Agent 標準鏈",
        "Capability 與 legacy Flow Agent 只作 reference / diagnostic",
    ):
        require(task_trace, token)
    require(task_table, "Source Flow、Agent Pool 與 Pool Member Agent 設定")
    require(task_table, "Capability 只作 reference / diagnostics")


def assert_docs_and_makefile() -> None:
    # Phase 8B archived historical phase implementation records.
    # The Phase 4-6 release-gate evidence remains available for forensic review
    # under docs/archive, but docs/current is no longer phase-number driven.
    doc = "docs/archive/phase-series/current-history/development/phase4-6-current-legacy-isolation-release-gate.md"
    changelog = "docs/archive/phase-series/current-history/phase4-6-change-log.md"
    modified = "docs/archive/phase-series/current-history/phase4-6-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (doc, changelog, modified, readme, makefile):
        require_file(path)

    for token in (
        "Phase 4-6: Current / Legacy Isolation Release Gate Consolidation",
        "make verify-phase4-release-gate",
        "Current routing packages do not depend on legacy or governance routing",
        "`GenericAuthorityBridge` remains the only legacy bridge",
        "Legacy candidate APIs expose deprecation and replacement headers",
        "reference-only or diagnostic-only",
        "Source Flow -> Agent Pool -> Pool Member Agent",
        "Phase 4-6 does not",
    ):
        require(doc, token)

    for token in (
        "Phase 4-6",
        "Behavior changes",
        "None",
    ):
        require(changelog, token)

    require(modified, "scripts/verify/verify-phase4-release-gate.py")
    require(modified, "Phase 4-6 is a release-gate consolidation only")
    # Phase 8A removes historical phase links from docs/current/README.md.
    # Phase 4-6 release-gate history is preserved by its own doc and archive plan.
    require("docs/archive/historical-asset-governance.md", "Completed Phase 8B relocation")
    require("docs/catalog.yml", "total_archived_items")
    require("docs/archive/legacy-verification/README.md", "archive-only")
    require(makefile, "verify-phase4-release-gate")
    require(makefile, "verify-phase4-5-current-setup-navigation-hardening")
    require(makefile, "verify-phase4-release-gate.py")


def main() -> None:
    # Run the Phase 4 chain through the latest Phase 4-5 verifier first.
    subprocess.run([sys.executable, str(ROOT / "scripts/verify/verify-phase4-5-current-setup-navigation-hardening.py")], check=True)

    # Then assert the consolidated release gate invariants directly.
    assert_current_routing_boundary()
    assert_bridge_access_is_restricted()
    assert_legacy_api_deprecation_headers()
    assert_admin_ui_labeling_and_navigation()
    assert_docs_and_makefile()
    assert_no_phase4_schema_migration()

    # Keep previous phase release gate present as the Phase 4 base.
    require_file("scripts/verify/verify-phase3-release-gate.py")
    require_file("scripts/verify/verify-phase4-1-current-legacy-boundary-plan.py")
    require_file("scripts/verify/verify-phase4-2-current-path-legacy-dependency-inventory.py")
    require_file("scripts/verify/verify-phase4-3-legacy-api-deprecation-headers.py")
    require_file("scripts/verify/verify-phase4-4-admin-ui-legacy-surface-labeling.py")
    require_file("scripts/verify/verify-phase4-5-current-setup-navigation-hardening.py")

    print("Phase 4 release gate contract verified.")


if __name__ == "__main__":
    main()
