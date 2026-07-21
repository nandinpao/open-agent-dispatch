#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
ROUTING_ROOT = ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing"


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require_file(path: str) -> None:
    if not (ROOT / path).is_file():
        print(f"Missing required file: {path}", file=sys.stderr)
        sys.exit(1)


def require_dir(path: str) -> None:
    if not (ROOT / path).is_dir():
        print(f"Missing required directory: {path}", file=sys.stderr)
        sys.exit(1)


def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        print(f"Missing token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def forbid(path: str, token: str) -> None:
    text = read(path)
    if token in text:
        print(f"Forbidden token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def assert_no_phase4_schema_migration() -> None:
    migration_dir = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"
    forbidden = []
    for pattern in ("V7__phase4*.sql", "V7__*legacy*.sql", "V7__*deprecat*.sql"):
        forbidden.extend(sorted(migration_dir.glob(pattern)))
    if forbidden:
        print("Phase 4-2 must not add schema migrations:", file=sys.stderr)
        for path in forbidden:
            print(f" - {path.relative_to(ROOT)}", file=sys.stderr)
        sys.exit(1)


def assert_current_packages_still_isolated() -> None:
    current_dirs = (
        "selection",
        "eligibility",
        "pool",
        "flow",
        "evidence",
        "scoring",
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
    for child in current_dirs:
        directory = ROUTING_ROOT / child
        require_dir(str(directory.relative_to(ROOT)))
        files = sorted(directory.rglob("*.java"))
        if not files:
            print(f"Current routing package has no Java files: {directory.relative_to(ROOT)}", file=sys.stderr)
            sys.exit(1)
        for path in files:
            text = path.read_text(encoding="utf-8")
            for token in forbidden_tokens:
                if token in text:
                    print(f"Current routing package legacy dependency violation: {path.relative_to(ROOT)} contains {token}", file=sys.stderr)
                    sys.exit(1)


def main() -> None:
    # Phase 4-2 depends on Phase 4-1 package-boundary rules.
    subprocess.run([sys.executable, str(ROOT / "scripts/verify/verify-phase4-1-current-legacy-boundary-plan.py")], check=True)

    doc = "docs/current/development/phase4-2-current-path-legacy-dependency-inventory.md"
    changelog = "docs/current/phase4-2-change-log.md"
    modified = "docs/current/phase4-2-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"
    verifier = "scripts/verify/verify-phase4-2-current-path-legacy-dependency-inventory.py"

    for path in (doc, changelog, modified, verifier, readme, makefile):
        require_file(path)

    # Required Current API surfaces must still exist.
    for path in (
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/SourceSystemController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentGovernanceController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskDispatchEvidenceController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/EventIntakeController.java",
    ):
        require_file(path)

    # Required legacy / governance candidate surfaces must still be documented before Phase 4-3 headers.
    for path in (
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchContractController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchCutoverController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchEligibilityController.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/EnforceOperationsController.java",
    ):
        require_file(path)

    # Required UI surfaces must still exist so they can be classified before UI cleanup.
    for path in (
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx",
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx",
        "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
        "ai-event-gateway-admin-ui/components/tasks/BeginnerTaskFlowPanel.tsx",
        "ai-event-gateway-admin-ui/components/dispatch-evidence/DispatchAssignmentEvidencePanel.tsx",
        "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts",
        "ai-event-gateway-admin-ui/lib/types/core.ts",
    ):
        require_file(path)

    assert_current_packages_still_isolated()
    assert_no_phase4_schema_migration()

    # Documentation must include the inventory dimensions and non-goals.
    for token in (
        "Phase 4-2: Current Path Legacy Dependency Inventory",
        "Inventory scope",
        "Source Flow / Agent Pool path dependency inventory",
        "Current API inventory",
        "Legacy / governance / diagnostic candidate APIs",
        "Current UI inventory",
        "UI legacy / profile / capability concepts still visible",
        "Deprecated API / replacement inventory",
        "Phase 4-3 input",
        "Deprecation: true",
        "X-OpenDispatch-Replacement",
        "Phase 4-2 does not",
        "does not add a Flyway migration",
    ):
        require(doc, token)

    # The inventory must name key Current replacements and legacy candidates.
    for token in (
        "/admin/source-systems",
        "/admin/dispatch-flows",
        "/admin/dispatch-flows/{flowId}/readiness",
        "/admin/tasks/{taskId}/dispatch-evidence",
        "/api/events/intake",
        "/admin/dispatch-contracts/**",
        "/admin/dispatch-policies/**",
        "/admin/capabilities/**",
        "/admin/dispatch-governance/cutover/**",
        "/admin/tasks/{taskId}/eligible-agents-v2",
        "Source Flow Rule + Agent Pool",
        "Capability reference-only",
        "GenericAuthorityBridge",
        "DispatchContractController",
        "AgentAssignmentController",
        "DispatchCutoverController",
        "DispatchEligibilityController",
        "DispatchContractBuilderConsole.tsx",
        "BeginnerTaskFlowPanel.tsx",
        "DispatchAssignmentEvidencePanel.tsx",
        "coreAdminApi.ts",
    ):
        require(doc, token)

    # Known code tokens prove the documented inventory corresponds to actual surfaces.
    require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchContractController.java", "AgentAssignmentService")
    require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java", "DispatchPolicyRequiredCapability")
    require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchCutoverController.java", "DispatchCutoverService")
    require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchEligibilityController.java", "DispatchEligibilityService")
    require("ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx", "Legacy Routing Reference")
    require("ai-event-gateway-admin-ui/components/tasks/BeginnerTaskFlowPanel.tsx", "requiredCapabilities")
    require("ai-event-gateway-admin-ui/components/dispatch-evidence/DispatchAssignmentEvidencePanel.tsx", "requiredCapabilities")
    require("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts", "CoreAgentAssignmentProfile")

    # README and Makefile expose the phase.
    require(readme, "development/phase4-2-current-path-legacy-dependency-inventory.md")
    require(readme, "phase-4-2-current-path-legacy-dependency-inventory")
    require(readme, "make verify-phase4-2-current-path-legacy-dependency-inventory")
    require(makefile, "verify-phase4-2-current-path-legacy-dependency-inventory")
    require(makefile, "verify-phase4-2-current-path-legacy-dependency-inventory.py")

    require(changelog, "Behavior changes")
    require(changelog, "None")
    require(changelog, "API behavior changes")
    require(changelog, "Deprecation headers are planned for Phase 4-3")
    require(modified, "Not modified")
    require(modified, "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/**/*.java")

    print("Phase 4-2 current path legacy dependency inventory contract verified.")


if __name__ == "__main__":
    main()
