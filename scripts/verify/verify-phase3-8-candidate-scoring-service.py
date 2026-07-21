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
    scoring = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/scoring/CandidateScoringService.java"
    doc = "docs/current/development/phase3-8-candidate-scoring-service.md"
    changelog = "docs/current/phase3-8-change-log.md"
    files = "docs/current/phase3-8-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (
        service,
        scoring,
        doc,
        changelog,
        files,
        "scripts/verify/verify-phase3-8-candidate-scoring-service.py",
    ):
        require_file(path)

    # RoutingDecisionService remains the public facade and delegates score construction.
    for token in (
        "public class RoutingDecisionService",
        "private CandidateScoringService candidateScoringService()",
        "new CandidateScoringService(properties, runtimeEligibilityEvaluator,",
        "CandidateScoringService candidateScoringService = candidateScoringService();",
        "boolean flowRuleTask = isFlowRuleTask(task);",
        "candidateScoringService.score(task, agent, policy, flowRuleTask)",
        "selectionStrategyRegistry.annotate(score, selectionContext)",
        "applyV2ScoreAnnotations(score, v2Comparison, eligibilityMode)",
    ):
        require(service, token)

    forbid(service, "private AgentCandidateScore score(TaskRecord task, AgentSnapshot agent, RoutingPolicy policy)")
    forbid(service, "capabilityScore + availabilityScore + slotScore + loadScore")
    forbid(service, "scoreBreakdown.put(\"capabilityScore\"")

    # CandidateScoringService owns the scoring formula and stable evidence keys.
    for token in (
        "package com.opensocket.aievent.core.routing.scoring;",
        "public class CandidateScoringService",
        "public AgentCandidateScore score(TaskRecord task, AgentSnapshot agent, RoutingPolicy policy, boolean flowRuleTask)",
        "BackendEligibilityScore backendEligibility = evaluateBackendEligibility(task, agent);",
        "int capabilityScore = requiredCapabilities.isEmpty()",
        "int availabilityScore = runtimeAssignable ? 20 : 0;",
        "int slotScore = Math.min(10, Math.max(0, agent.getAvailableSlots()) * 5);",
        "int loadScore = runtimeLoadScore(agent, policy);",
        "int siteScore = task.getSiteId() != null && task.getSiteId().equals(agent.getSiteId()) ? 10 : 0;",
        "int healthScore = Math.round(agent.getHealthScore() / 10.0f);",
        "int policyBonus = policyBonus(policy, missing, siteScore);",
        "int penalty = runtimePenalty(agent);",
        "SkillScore skill = evaluateSkillAware(task, agent, flowRuleTask);",
        "SkillVersionScore skillVersion = evaluateSkillVersionCompatibility(task, agent);",
        "int finalScore = backendEligibility.blockingFailure() ? 0 : (!runtimeAssignable || hasMissing) ? Math.min(score, 49) : score;",
        "return new AgentCandidateScore(",
    ):
        require(scoring, token)

    for key in (
        "capabilityScore",
        "availabilityScore",
        "slotScore",
        "loadScore",
        "siteScore",
        "healthScore",
        "policyBonus",
        "runtimePenalty",
        "runtimeAssignable",
        "agentStatus",
        "runtimeCapacityAvailable",
        "runtimeCapacityFull",
        "backendEligibilityApplied",
        "backendEligibilityScore",
        "backendEligibilityPenalty",
        "backendEligibilityReason",
        "backendEligibilityRequiredProfiles",
        "backendEligibilityApprovedProfiles",
        "backendEligibilityBlocking",
        "skillApplied",
        "skillScore",
        "skillPenalty",
        "skillVersionApplied",
        "skillVersionScore",
        "skillVersionPenalty",
        "rawScore",
        "finalScore",
        "routingPolicy",
        "routingPath",
        "matchedFlowId",
        "matchedRuleId",
        "requestedSkill",
        "matchedCapabilities",
        "missingCapabilities",
        "effectiveCapabilities",
        "skillReason",
        "skillVersionReason",
        "blockingFailure",
        "runtime",
    ):
        require(scoring, f"scoreBreakdown.put(\"{key}\"")

    # Source Flow / Pool-first capability behavior must remain reference-only.
    for token in (
        "private List<String> routingRequiredCapabilities(TaskRecord task, boolean flowRuleTask)",
        "if (flowRuleTask) {",
        "return List.of();",
        "must not turn requestedSkill or required_capabilities_json into a blocking gate",
    ):
        require(scoring, token)

    # WEIGHTED_SCORE continues to consume AgentCandidateScore.score() after base score construction.
    selection = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/WeightedScoreSelectionStrategy.java"
    abstract_selection = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/AbstractPoolSelectionStrategy.java"
    require_file(selection)
    require_file(abstract_selection)
    require(selection, "AgentCandidateScore::score")
    require(abstract_selection, "poolWeightedEffectiveScore")

    for token in (
        "Phase 3-8: Extract CandidateScoringService",
        "behavior-preserving",
        "scoreBreakdown key names",
        "backend eligibility blocking behavior",
        "skill-aware scoring behavior",
        "skill-version compatibility scoring behavior",
        "WEIGHTED_SCORE access to the same base AgentCandidateScore.score()",
    ):
        require(doc, token)

    require(changelog, "None intended")
    require(files, "CandidateScoringService.java")
    require(files, "RoutingDecisionService.java")
    require(readme, "development/phase3-8-candidate-scoring-service.md")
    require(readme, "make verify-phase3-8-candidate-scoring-service")
    require(makefile, "verify-phase3-8-candidate-scoring-service")
    require(makefile, "verify-phase3-8-candidate-scoring-service.py")

    service_line_count = len(read(service).splitlines())
    scoring_line_count = len(read(scoring).splitlines())
    if service_line_count >= 1140:
        print(f"RoutingDecisionService line count did not decrease from Phase 3-7 baseline: {service_line_count}", file=sys.stderr)
        sys.exit(1)
    if scoring_line_count < 400:
        print(f"CandidateScoringService is unexpectedly small; scoring formula was likely not extracted: {scoring_line_count}", file=sys.stderr)
        sys.exit(1)

    print("Phase 3-8 candidate scoring service contract verified.")


if __name__ == "__main__":
    main()
