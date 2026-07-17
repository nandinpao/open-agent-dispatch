#!/usr/bin/env python3
"""Verify P4 generic candidate pools and routing ranking remain source-agnostic and shadow-only."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(relative: str, fragments: list[str]) -> str:
    text = read(relative)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{relative} is missing contract fragment: {fragment}")
    return text


def forbid_named_sources(relative: str) -> None:
    text = read(relative)
    for token in ('"ERP"', '"MES"', '"CMS"', '"HR"', '"WMS"', 'tenant-a', 'agent-cluster-node'):
        if token in text:
            fail(f"P4 generic file contains forbidden source-specific token {token}: {relative}")


def main() -> int:
    routing_dir = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/"
    model_dir = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/"

    require(model_dir + "GenericRoutingStrategy.java", [
        "CAPABILITY_FIRST", "LOWEST_LOAD", "ROUND_ROBIN", "LOCAL_FIRST",
        "WEIGHTED_SCORE", "MANUAL_REVIEW",
    ])
    require(routing_dir + "CandidateAgentProvider.java", ["Map<String, GenericCandidateAgent> provide"])
    provider = require(routing_dir + "GenericCandidateAgentProvider.java", [
        "CandidatePoolMode.LEGACY",
        "CandidatePoolMode.EXPLICIT_FLOW_AGENTS",
        "CandidatePoolMode.SOURCE_SYSTEM_POOL",
        "CandidatePoolMode.CAPABILITY_MATCHED_POOL",
        "findExplicitFlowAgentIds",
        "findCapabilityMatchedAgentIds",
        "sourceAssignments.search",
    ])
    forbid_named_sources(routing_dir + "GenericCandidateAgentProvider.java")
    if "switch(sourceSystem" in provider or "switch (sourceSystem" in provider:
        fail("P4 CandidateAgentProvider must not branch on sourceSystem")

    calculator = require(routing_dir + "GenericRoutingScoreCalculator.java", [
        "case CAPABILITY_FIRST",
        "case LOWEST_LOAD",
        "case LOCAL_FIRST",
        "case ROUND_ROBIN",
        "case MANUAL_REVIEW",
        "breakdown.put(\"strategy\"",
    ])
    forbid_named_sources(routing_dir + "GenericRoutingScoreCalculator.java")
    if "getSourceSystem" in calculator:
        fail("P4 routing score must not inspect sourceSystem")

    service = require(routing_dir + "DispatchRoutingShadowService.java", [
        "authoritativeRoutingUnchanged=true",
        "candidateProvider.provide",
        "evaluateCandidateForRoutingShadow",
        "LEGACY_ONLY_CANDIDATE",
        "SHADOW_ONLY_CANDIDATE",
        "RANK_CHANGED",
        "SELECTED_AGENT_CHANGED",
        "MANUAL_REVIEW_REQUIRED",
    ])
    forbid_named_sources(routing_dir + "DispatchRoutingShadowService.java")

    routing = require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
        [
            "observeGenericEligibilityShadow(task, candidatePool.included(), scores)",
            "observeGenericRoutingShadow(task, candidatePool.included(), scores)",
            "genericRoutingShadowService.observe(task, candidates, scores, properties.getMinimumScore())",
        ],
    )
    p3_index = routing.find("observeGenericEligibilityShadow(task, candidatePool.included(), scores)")
    p4_index = routing.find("observeGenericRoutingShadow(task, candidatePool.included(), scores)", p3_index)
    enforce_index = routing.find("if (eligibilityMode.enforce()", p4_index)
    if min(p3_index, p4_index, enforce_index) < 0 or not (p3_index < p4_index < enforce_index):
        fail("P4 routing observer must run after P3 observation and before authoritative eligibility filtering")

    require(
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcGenericCandidateAgentRepository.java",
        ["flow_agent_assignments", "agent_capability_assignments", "requiredCount", "expires_at"],
    )
    forbid_named_sources(
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcGenericCandidateAgentRepository.java"
    )

    require(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V119__p4_generic_routing_strategy_contract.sql",
        ["default_routing_strategy", "candidate_pool_mode", "routing_strategy", "WEIGHTED_SCORE", "MANUAL_REVIEW"],
    )
    migration = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V120__p4_candidate_ranking_shadow_comparison.sql"
    require(migration, [
        "task_agent_routing_shadow_comparisons",
        "candidate_pool_mode",
        "routing_strategy",
        "legacy_rank",
        "shadow_rank",
        "append-only",
        "dispatch_p4_candidate_ranking_shadow_difference_report",
        "dispatch_p4_candidate_ranking_shadow_summary",
    ])
    forbid_named_sources(migration)

    require("ai-event-gateway-core/control-plane-app/src/main/resources/application.yml", [
        "generic-routing-shadow-enabled",
        "generic-routing-shadow-persist-comparisons",
        "generic-routing-shadow-max-candidates",
    ])
    require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchGovernanceController.java", [
        "/agent-routing-shadow-comparisons",
        "/tasks/{taskId}/agent-routing-shadow-comparisons",
    ])
    require("ai-event-gateway-core/architecture/table-ownership.csv", [
        "task_agent_routing_shadow_comparisons,task-orchestration",
    ])
    require("ai-event-gateway-core/architecture/baseline/m8-cross-context-repository-imports.csv", [
        "JdbcGenericCandidateAgentRepository.java",
        "JdbcTaskAgentRoutingShadowComparisonRepository.java",
    ])
    require(
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/governance/routing/GenericRoutingScoreCalculatorTest.java",
        ["scoresOpaqueSourceAndCapabilityWithoutDomainInference", "manualReviewDoesNotAutoRankAnAgent"],
    )

    java_harness = subprocess.run(
        [str(ROOT / "scripts/verify/verify-p4-routing-shadow-java.sh")], cwd=ROOT
    )
    if java_harness.returncode != 0:
        fail("P4 Java compile/routing harness failed")

    # The standard release sequence runs P0 before P4. Avoid scanning the full
    # repository a fifth time here; standalone users should run both targets.
    require("scripts/architecture/zero-special-case-baseline.json", ["SOURCE_LITERAL_DECISION"])

    print("[PASS] P4 generic candidate pools, routing strategies, and shadow ranking comparisons verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
