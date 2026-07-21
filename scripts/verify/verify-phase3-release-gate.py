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


def require_any(path: str, tokens: tuple[str, ...]) -> None:
    text = read(path)
    if not any(token in text for token in tokens):
        print(f"Missing one of tokens in {path}: {tokens}", file=sys.stderr)
        sys.exit(1)


def assert_no_legacy_dependency(paths: tuple[str, ...]) -> None:
    forbidden = ("routing.legacy", "GenericAuthorityBridge")
    for path in paths:
        text = read(path)
        for token in forbidden:
            if token in text:
                print(f"Current routing component must not depend on legacy bridge: {path} contains {token}", file=sys.stderr)
                sys.exit(1)


def main() -> None:
    # Phase 3 local verifiers must remain present. The release gate performs a
    # consolidated cross-phase contract check instead of recursively invoking the
    # long Make dependency chain, so it stays usable under short CI/tool timeouts.
    for verifier in (
        "scripts/verify/verify-phase3-1-routing-responsibility-extraction-plan.py",
        "scripts/verify/verify-phase3-2-selection-strategy-registry.py",
        "scripts/verify/verify-phase3-3-runtime-eligibility-evaluator.py",
        "scripts/verify/verify-phase3-4-pool-resolver.py",
        "scripts/verify/verify-phase3-5-flow-rule-resolver.py",
        "scripts/verify/verify-phase3-6-routing-evidence-blocker-builders.py",
        "scripts/verify/verify-phase3-7-routing-orchestrator-facade.py",
        "scripts/verify/verify-phase3-8-candidate-scoring-service.py",
        "scripts/verify/verify-phase3-9-generic-authority-bridge.py",
    ):
        require_file(verifier)

    service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java"
    orchestrator = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingOrchestrator.java"
    selection = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/SelectionStrategyRegistry.java"
    lowest = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/LowestLoadSelectionStrategy.java"
    weighted = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/WeightedScoreSelectionStrategy.java"
    manual = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/ManualOnlySelectionStrategy.java"
    eligibility = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/eligibility/RuntimeEligibilityEvaluator.java"
    candidate_filter = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/eligibility/CandidateFilterResult.java"
    pool = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/pool/PoolResolver.java"
    flow = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/flow/FlowResolver.java"
    rule = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/flow/RuleResolver.java"
    flow_resolution = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/flow/FlowResolution.java"
    evidence = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/evidence/RoutingEvidenceBuilder.java"
    blocker = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/evidence/RoutingBlockerResolver.java"
    scoring = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/scoring/CandidateScoringService.java"
    bridge = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/legacy/GenericAuthorityBridge.java"

    java_files = (
        service, orchestrator, selection, lowest, weighted, manual, eligibility,
        candidate_filter, pool, flow, rule, flow_resolution, evidence, blocker,
        scoring, bridge,
    )
    for path in java_files:
        require_file(path)

    # RoutingDecisionService should now be a facade / boundary, not the owner of
    # strategy, pool, flow, evidence, scoring, or generic authority internals.
    for token in (
        "public class RoutingDecisionService",
        "routingOrchestrator().decide(task, excludedAgentIds)",
        "private RoutingOrchestrator routingOrchestrator()",
        "private final SelectionStrategyRegistry selectionStrategyRegistry = SelectionStrategyRegistry.createDefault();",
        "private final RuntimeEligibilityEvaluator runtimeEligibilityEvaluator;",
        "FlowResolver flowResolver()",
        "private PoolResolver poolResolver()",
        "RoutingEvidenceBuilder routingEvidenceBuilder()",
        "RoutingBlockerResolver routingBlockerResolver()",
        "private CandidateScoringService candidateScoringService()",
        "RoutingCandidateSelection selectCandidates(",
        "selectionStrategyRegistry.select(scores, selectionContext)",
        "GenericAuthorityBridge genericAuthorityBridge()",
        "RoutingDecisionRecord saveAndRecord(RoutingDecisionRecord decision)",
    ):
        require(service, token)
    for token in (
        "private RoutingDecisionRecord decideObserved(",
        "private AgentCandidateScore score(TaskRecord task, AgentSnapshot agent, RoutingPolicy policy)",
        "private CandidateFilterResult filterCandidates(",
        "private CandidateFilterResult resolveCandidatePool(",
        "RoutingDecisionRecord decideWithGenericAuthority(",
        "private Comparator<AgentCandidateScore> poolComparator(",
        "scoreBreakdown.put(\"capabilityScore\"",
    ):
        forbid(service, token)

    # Extracted components and preserved contract markers.
    for token in (
        "public final class SelectionStrategyRegistry",
        "LOWEST_LOAD",
        "WEIGHTED_SCORE",
        "MANUAL_ONLY",
        "private final AgentSelectionStrategy fallback",
    ):
        require(selection, token)
    for token in ("public final class LowestLoadSelectionStrategy", "effectiveTaskCount", "memberPriority", "memberWeight"):
        require(lowest, token)
    for token in (
        "public final class WeightedScoreSelectionStrategy",
        "Math.round((safeBaseScore * 0.8d)",
        "normalizedMemberWeight",
    ):
        require(weighted, token)
    for token in (
        "poolWeightedEffectiveScore",
        "poolWeightFormula",
        "poolBaseScore",
        "poolMemberWeight",
    ):
        require("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/AbstractPoolSelectionStrategy.java", token)
    for token in ("public final class ManualOnlySelectionStrategy", "manualOnly()", "return true"):
        require(manual, token)

    for token in (
        "public class RuntimeEligibilityEvaluator",
        "public CandidateFilterResult filterCandidates(",
        "includeForScoring",
        "POOL_AGENT_RUNTIME_NOT_FOUND",
        "POOL_AGENT_OFFLINE",
        "POOL_AGENT_CAPACITY_FULL",
        "POOL_AGENT_BACKOFF",
        "NO_ELIGIBLE_AGENT_IN_POOL",
    ):
        require(eligibility, token)
    for token in ("public record CandidateFilterResult", "memberCount()", "eligibleAgentCount()", "withPool(", "blocked("):
        require(candidate_filter, token)

    for token in (
        "public class PoolResolver",
        "public CandidateFilterResult resolve(",
        "RULE_TARGET_POOL_NOT_FOUND",
        "POOL_HAS_NO_ACTIVE_MEMBER",
        "routing_pool_snapshot",
        "routing_manual_only_pool_without_members",
    ):
        require(pool, token)

    for token in (
        "public class FlowResolver",
        "public FlowResolution resolve(TaskRecord task)",
        "isSourceFlowPoolFirstTask",
        "decisionSuffix(TaskRecord task)",
        "requiredSkills(TaskRecord task)",
    ):
        require(flow, token)
    for token in (
        "public class RuleResolver",
        "FlowRuleRoutingService",
        "routing_flow_rule_runtime_repaired",
        "routing_flow_rule_runtime_repair_not_matched",
        "routing_flow_rule_runtime_repair_failed",
    ):
        require(rule, token)
    require(flow_resolution, "public record FlowResolution")

    for token in (
        "public final class RoutingEvidenceBuilder",
        "candidateTrace",
        "manualOnlyDecisionReason",
        "selectedDecisionReason",
        "scoreBreakdown",
    ):
        require(evidence, token)
    for token in (
        "public final class RoutingBlockerResolver",
        "noCandidateError",
        "belowMinimumError",
        "observationBlockingReasonCode",
        "DISPATCH_NO_AGENT_ONLINE",
        "POOL_AGENT_RUNTIME_NOT_FOUND",
    ):
        require(blocker, token)

    for token in (
        "class RoutingOrchestrator",
        "FlowResolution flowResolution = service.flowResolver().resolve(task);",
        "RoutingDecisionService.RoutingCandidateSelection candidateSelection = service.selectCandidates(task, excluded, policy, v2Comparison, eligibilityMode);",
        "CandidateFilterResult candidatePool = candidateSelection.candidatePool();",
        "service.genericAuthorityBridge().decide(task, excluded, decision, service.isFlowRuleTask(task))",
        "if (!service.isSourceFlowPoolFirstTask(task))",
        "routing_source_flow_pool_first_bypassed_generic_authority",
        "SOURCE_FLOW_POOL_IS_AUTHORITATIVE",
        "routingModel=AGENT_POOL_FIRST",
    ):
        require(orchestrator, token)

    for token in (
        "public class CandidateScoringService",
        "public AgentCandidateScore score(TaskRecord task, AgentSnapshot agent, RoutingPolicy policy, boolean flowRuleTask)",
        "capabilityScore",
        "availabilityScore",
        "backendEligibilityScore",
        "skillVersionScore",
        "scoreBreakdown",
        "finalScore",
    ):
        require(scoring, token)

    for token in (
        "package com.opensocket.aievent.core.routing.legacy;",
        "public class GenericAuthorityBridge",
        "DispatchCutoverService",
        "GenericDispatchAuthoritativeService",
        "public RoutingDecisionRecord decide(TaskRecord task,",
        "if (task == null || !flowRuleTask)",
        "P11 generic authority is required for new Flow work and is unavailable",
        "generic_dispatch_non_authoritative_held",
        "generic_dispatch_authoritative_no_selection",
        "generic_dispatch_authoritative_completed",
    ):
        require(bridge, token)

    # Current components must not depend on the legacy bridge. The only allowed
    # references are the service bridge factory, the orchestrator non-current path,
    # and GenericAuthorityBridge itself.
    assert_no_legacy_dependency((selection, lowest, weighted, manual, eligibility, candidate_filter, pool, flow, rule, flow_resolution, evidence, blocker, scoring))
    for path in (
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/SelectionStrategyRegistry.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/eligibility/RuntimeEligibilityEvaluator.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/pool/PoolResolver.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/flow/FlowResolver.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/evidence/RoutingBlockerResolver.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/scoring/CandidateScoringService.java",
    ):
        forbid(path, "com.opensocket.aievent.core.routing.legacy")

    # Line-count gate: Phase 3 should leave the former god service below a
    # conservative facade threshold. This does not replace compilation tests; it
    # prevents accidental re-growth of the service during the consolidation phase.
    service_line_count = len(read(service).splitlines())
    if service_line_count > 650:
        print(f"RoutingDecisionService is too large for Phase 3 release gate: {service_line_count} lines", file=sys.stderr)
        sys.exit(1)

    # Documentation and Make target.
    doc = "docs/current/development/phase3-10-routing-core-release-gate.md"
    changelog = "docs/current/phase3-10-change-log.md"
    files = "docs/current/phase3-10-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"
    for path in (doc, changelog, files, "scripts/verify/verify-phase3-release-gate.py"):
        require_file(path)
    for token in (
        "Phase 3-10: Routing Core Release Gate Consolidation",
        "verify-phase3-release-gate",
        "RoutingDecisionService is now a facade",
        "selection / eligibility / pool / flow / evidence / blocker / orchestrator / scoring / legacy bridge",
        "Current routing components must not depend on `routing.legacy`",
        "GenericAuthorityBridge is reachable only from the non-current branch of `RoutingOrchestrator`",
    ):
        require(doc, token)
    require(changelog, "No runtime behavior change intended")
    require(files, "scripts/verify/verify-phase3-release-gate.py")
    require(readme, "development/phase3-10-routing-core-release-gate.md")
    require(readme, "make verify-phase3-release-gate")
    require(makefile, "verify-phase3-release-gate")
    require(makefile, "verify-phase3-release-gate.py")

    print("Phase 3 release gate contract verified.")


if __name__ == "__main__":
    main()
