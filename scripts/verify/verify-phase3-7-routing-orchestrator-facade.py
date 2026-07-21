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


def forbid(path: str, token: str) -> None:
    text = read(path)
    if token in text:
        print(f"Forbidden token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java"
    orchestrator = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingOrchestrator.java"
    doc = "docs/current/development/phase3-7-routing-orchestrator-facade.md"
    changelog = "docs/current/phase3-7-change-log.md"
    files = "docs/current/phase3-7-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (
        service,
        orchestrator,
        doc,
        changelog,
        files,
        "scripts/verify/verify-phase3-7-routing-orchestrator-facade.py",
    ):
        require_file(path)

    # RoutingDecisionService remains the public facade and observation wrapper.
    for token in (
        "public class RoutingDecisionService",
        "public RoutingDecisionRecord decide(TaskRecord task, Set<String> excludedAgentIds)",
        "RoutingDecisionRecord decision = routingOrchestrator().decide(task, excludedAgentIds);",
        "private RoutingOrchestrator routingOrchestrator()",
        "recordAssignmentDecision(observation, decision)",
        "RoutingDecisionRecord saveAndRecord(RoutingDecisionRecord decision)",
        "RoutingProperties properties()",
        "RoutingEvidenceBuilder routingEvidenceBuilder()",
        "RoutingBlockerResolver routingBlockerResolver()",
        "RoutingCandidateSelection selectCandidates(",
    ):
        require(service, token)

    forbid(service, "private RoutingDecisionRecord decideObserved")

    # RoutingOrchestrator owns high-level routing sequence.
    for token in (
        "class RoutingOrchestrator",
        "RoutingDecisionRecord decide(TaskRecord task, Set<String> excludedAgentIds)",
        "Set<String> excluded = service.normalizeAgentIds(excludedAgentIds);",
        "FlowResolution flowResolution = service.flowResolver().resolve(task);",
        "RoutingPolicy policy = flowResolution.policy();",
        "routing_decision_started",
        "service.properties().isAssignmentEnabled()",
        "service.genericAuthorityBridge().decide(task, excluded, decision, service.isFlowRuleTask(task))",
        "service.requiresManualReview(policy)",
        "service.selectCandidates(task, excluded, policy, v2Comparison, eligibilityMode)",
        "service.isManualOnlyPool(candidatePool)",
        "RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED",
        "RoutingDecisionStatus.NO_CANDIDATE",
        "RoutingDecisionStatus.SELECTED",
        "service.routingBlockerResolver().noCandidateError(task, candidatePool)",
        "service.routingEvidenceBuilder().selectedDecisionReason(",
        "service.saveAndRecord(decision)",
    ):
        require(orchestrator, token)

    for token in (
        "Phase 3-7: Introduce RoutingOrchestrator Facade",
        "behavior-preserving",
        "RoutingDecisionService.decide(...)",
        "RoutingOrchestrator.decide(...)",
        "MANUAL_ONLY -> MANUAL_REVIEW_REQUIRED",
        "NO_CANDIDATE user-facing diagnostics",
        "SELECTED decision reason",
        "saveAndRecord side effect",
        "recordAssignmentDecision observation side effect",
    ):
        require(doc, token)

    require(changelog, "None intended")
    require(files, "RoutingOrchestrator.java")
    require(files, "RoutingDecisionService.java")
    require(readme, "development/phase3-7-routing-orchestrator-facade.md")
    require(readme, "make verify-phase3-7-routing-orchestrator-facade")
    require(makefile, "verify-phase3-7-routing-orchestrator-facade")
    require(makefile, "verify-phase3-7-routing-orchestrator-facade.py")

    service_line_count = len(read(service).splitlines())
    orchestrator_line_count = len(read(orchestrator).splitlines())
    if service_line_count >= 1250:
        print(f"RoutingDecisionService line count did not decrease from Phase 3-6 baseline: {service_line_count}", file=sys.stderr)
        sys.exit(1)
    if orchestrator_line_count < 120:
        print(f"RoutingOrchestrator is unexpectedly small; main orchestration was likely not moved: {orchestrator_line_count}", file=sys.stderr)
        sys.exit(1)

    print("Phase 3-7 routing orchestrator facade contract verified.")


if __name__ == "__main__":
    main()
