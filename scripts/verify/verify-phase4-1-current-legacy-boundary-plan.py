#!/usr/bin/env python3
from pathlib import Path
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


def java_files_under(relative_dir: str) -> list[Path]:
    directory = ROOT / relative_dir
    return sorted(directory.rglob("*.java"))


def assert_current_package_boundary() -> None:
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
        require_dir(directory)
        files = java_files_under(directory)
        if not files:
            print(f"Current routing package has no Java files: {directory}", file=sys.stderr)
            sys.exit(1)
        for path in files:
            rel = path.relative_to(ROOT).as_posix()
            text = path.read_text(encoding="utf-8")
            for token in forbidden_tokens:
                if token in text:
                    print(f"Current routing package boundary violation: {rel} contains {token}", file=sys.stderr)
                    sys.exit(1)


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
            print(f"GenericAuthorityBridge may only be referenced by boundary owners; found in {rel}", file=sys.stderr)
            sys.exit(1)


def assert_no_phase4_migration() -> None:
    migration_dir = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"
    phase4_migrations = sorted(migration_dir.glob("V7__phase4*.sql")) + sorted(migration_dir.glob("V7__*legacy*.sql"))
    if phase4_migrations:
        print("Phase 4-1 must not add schema migrations:", file=sys.stderr)
        for path in phase4_migrations:
            print(f" - {path.relative_to(ROOT)}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    doc = "docs/current/development/phase4-1-current-legacy-boundary-enforcement-plan.md"
    changelog = "docs/current/phase4-1-change-log.md"
    modified = "docs/current/phase4-1-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"
    service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java"
    orchestrator = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingOrchestrator.java"
    bridge = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/legacy/GenericAuthorityBridge.java"

    for path in (
        doc,
        changelog,
        modified,
        service,
        orchestrator,
        bridge,
        "scripts/verify/verify-phase3-release-gate.py",
        "scripts/verify/verify-phase4-1-current-legacy-boundary-plan.py",
    ):
        require_file(path)

    for directory in (
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/cutover",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/legacy",
    ):
        require_dir(directory)

    assert_current_package_boundary()
    assert_bridge_access_is_restricted()
    assert_no_phase4_migration()

    # Documentation must declare the boundary and non-goals.
    for token in (
        "Phase 4-1: Current / Legacy Package Boundary Enforcement Plan",
        "GenericAuthorityBridge",
        "Current routing component set",
        "Allowed boundary owners",
        "Legacy / governance routing inventory",
        "Deprecated API / legacy candidate inventory",
        "Phase 4-1 does not",
        "does not change routing behavior",
        "does not delete legacy code",
        "does not add a database migration",
    ):
        require(doc, token)

    # Current path must still bypass generic authority before the bridge is called.
    for token in (
        "if (!service.isSourceFlowPoolFirstTask(task))",
        "service.genericAuthorityBridge().decide(task, excluded, decision, service.isFlowRuleTask(task))",
        "routing_source_flow_pool_first_bypassed_generic_authority",
        "SOURCE_FLOW_POOL_IS_AUTHORITATIVE",
        "routingModel=AGENT_POOL_FIRST",
    ):
        require(orchestrator, token)

    # RoutingDecisionService remains the bridge factory owner only.
    for token in (
        "GenericAuthorityBridge genericAuthorityBridge()",
        "new GenericAuthorityBridge(properties, dispatchCutoverService, genericAuthoritativeService,",
        "routingEvidenceBuilder, this::saveAndRecord",
    ):
        require(service, token)
    forbid(service, "RoutingDecisionRecord decideWithGenericAuthority(")

    # Bridge stays in the legacy package and keeps generic authority behavior isolated.
    for token in (
        "package com.opensocket.aievent.core.routing.legacy;",
        "public class GenericAuthorityBridge",
        "DispatchCutoverService",
        "GenericDispatchAuthoritativeService",
        "GenericAuthoritativeRoutingResult",
        "P11 generic authority",
    ):
        require(bridge, token)

    # README and Makefile expose the phase without making it a behavior change.
    require(readme, "development/phase4-1-current-legacy-boundary-enforcement-plan.md")
    require(readme, "phase-4-1-current-legacy-boundary-enforcement-plan")
    require(makefile, "verify-phase4-1-current-legacy-boundary-plan")
    require(makefile, "verify-phase4-1-current-legacy-boundary-plan.py")
    require(changelog, "Behavior changes")
    require(changelog, "None")
    require(modified, "Not modified")
    require(modified, "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/**/*.java")

    print("Phase 4-1 current/legacy boundary enforcement plan contract verified.")


if __name__ == "__main__":
    main()
