#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require_file(path: str) -> None:
    if not (ROOT / path).is_file():
        print(f"Missing required file: {path}", file=sys.stderr)
        sys.exit(1)


def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        print(f"Missing token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    doc = "docs/current/development/phase3-1-routing-decision-service-extraction-plan.md"
    changelog = "docs/current/phase3-1-change-log.md"
    files = "docs/current/phase3-1-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"
    routing_service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java"

    for path in (doc, changelog, files, routing_service):
        require_file(path)

    # Phase identity and plan-only boundary.
    for token in (
        "Phase 3-1: RoutingDecisionService Responsibility Extraction Plan",
        "planning and contract phase only",
        "does not extract Java classes",
        "does not change `RoutingDecisionService.decide(...)` behavior",
        "does not change database schema",
        "does not change Admin UI behavior",
    ):
        require(doc, token)

    # Target component map must be explicit.
    for token in (
        "RoutingOrchestrator",
        "RoutingModeResolver",
        "FlowResolver",
        "RuleResolver",
        "PoolResolver",
        "RuntimeCandidateProvider",
        "RuntimeEligibilityEvaluator",
        "SelectionStrategyRegistry",
        "RoutingEvidenceBuilder",
        "RoutingBlockerResolver",
        "RoutingObservationRecorder",
        "RoutingDecisionWriter",
        "LegacyRoutingBridge",
    ):
        require(doc, token)

    # Current/legacy and behavior-preservation rules must be explicit.
    for token in (
        "`current.*` must not depend on `legacy.*`",
        "`MANUAL_ONLY` must not auto-assign",
        "`WEIGHTED_SCORE` formula must remain Phase 1-1 compatible",
        "Unsupported persisted strategies must continue normalizing to `LOWEST_LOAD`",
        "Capability remains reference-only in the Current path",
        "No new database migration is allowed in Phase 3-1",
    ):
        require(doc, token)

    # Extraction sequence must be stable and start with strategy extraction.
    for token in (
        "Phase 3-2: Extract SelectionStrategyRegistry",
        "Phase 3-3: Extract RuntimeEligibilityEvaluator",
        "Phase 3-4: Extract PoolResolver",
        "Phase 3-5: Extract FlowResolver / RuleResolver",
        "Phase 3-6: Extract Evidence and Blocker builders",
        "Phase 3-7: Introduce RoutingOrchestrator facade",
    ):
        require(doc, token)

    # Compare-before/after gates for the next implementation phases.
    for token in (
        "SourceSystem-only event -> default pool -> LOWEST_LOAD selected",
        "MANUAL_ONLY pool -> manual review, no selectedAgentId",
        "WEIGHTED_SCORE -> effective score includes member weight",
        "Generic Authority path remains compatible for non-Current tasks",
        "selectedAgentId",
        "poolSelectionStrategy",
    ):
        require(doc, token)

    # Verify the routing service still exists. Later Phase 3 extraction phases may remove
    # specific private helper methods, so this verifier must not freeze the pre-extraction body.
    for token in (
        "public class RoutingDecisionService",
        "routingOrchestrator().decide(task, excludedAgentIds)",
        "RoutingDecisionRecord saveAndRecord",
    ):
        require(routing_service, token)

    # Documentation and Make wiring.
    require(changelog, "Phase 3-1 establishes the `RoutingDecisionService` responsibility extraction plan")
    require(changelog, "None.")
    require(files, "Not changed")
    require(files, "RoutingDecisionService.java")
    require(readme, "development/phase3-1-routing-decision-service-extraction-plan.md")
    require(readme, "make verify-phase3-1-routing-responsibility-extraction-plan")
    require(makefile, "verify-phase3-1-routing-responsibility-extraction-plan")
    require(makefile, "verify-phase3-1-routing-responsibility-extraction-plan.py")

    print("Phase 3-1 routing responsibility extraction plan contract verified.")


if __name__ == "__main__":
    main()
