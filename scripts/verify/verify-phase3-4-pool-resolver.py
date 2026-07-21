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
    resolver = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/pool/PoolResolver.java"
    doc = "docs/current/development/phase3-4-pool-resolver.md"
    changelog = "docs/current/phase3-4-change-log.md"
    files = "docs/current/phase3-4-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (
        service,
        resolver,
        doc,
        changelog,
        files,
        "scripts/verify/verify-phase3-4-pool-resolver.py",
    ):
        require_file(path)

    # RoutingDecisionService must delegate candidate-pool resolution instead of
    # hosting the old resolveCandidatePool/queryFor body.
    for token in (
        "import com.opensocket.aievent.core.routing.pool.PoolResolver;",
        "private PoolResolver poolResolver()",
        "poolResolver().resolve(task, excluded, policy, isFlowRuleTask(task))",
    ):
        require(service, token)

    for token in (
        "private CandidateFilterResult resolveCandidatePool",
        "private AgentQuery queryFor",
        "private boolean requiresLocalSearch",
        "private boolean allowsGlobalFallback",
        "routing_pool_snapshot tenantId=",
        "routing_manual_only_pool_without_members tenantId=",
        "RULE_TARGET_POOL_NOT_FOUND routingModel=AGENT_POOL_FIRST",
        "POOL_HAS_NO_ACTIVE_MEMBER routingModel=AGENT_POOL_FIRST",
    ):
        forbid(service, token)

    # PoolResolver must own the pool-resolution behavior and preserve diagnostic strings.
    for token in (
        "public class PoolResolver",
        "public CandidateFilterResult resolve(TaskRecord task, Set<String> excluded, RoutingPolicy policy, boolean flowRuleTask)",
        "firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId())",
        "SOURCE_FLOW_HAS_NO_DEFAULT_POOL",
        "AGENT_POOL_REPOSITORY_UNAVAILABLE",
        "agentPoolRoutingRepository.findActivePool(task.getTenantId(), targetPoolId)",
        "RULE_TARGET_POOL_NOT_FOUND",
        "POOL_HAS_NO_ACTIVE_MEMBER",
        "routing_manual_only_pool_without_members",
        "SelectionStrategyRegistry.MANUAL_ONLY.equals(selectionStrategy)",
        "agentDirectory.findById(member.getAgentId()).ifPresent(candidates::add)",
        "runtimeEligibilityEvaluator.filterCandidates(candidates, excluded)",
        "runtimeEligibilityEvaluator.poolBlocker(pool, candidates, filtered)",
        "routing_pool_snapshot",
        "filtered.withPool(pool.getPoolId(), pool.getPoolCode(), selectionStrategy, membersByAgentId, poolBlocker)",
        "AgentQuery query = queryFor(task, maxCandidates, requiresLocalSearch(policy), false)",
        "allowsGlobalFallback(policy)",
    ):
        require(resolver, token)

    for token in (
        "Phase 3-4: Extract PoolResolver",
        "No routing behavior should change",
        "Source Flow task without target/default pool -> SOURCE_FLOW_HAS_NO_DEFAULT_POOL",
        "Rule target/default pool not found -> RULE_TARGET_POOL_NOT_FOUND",
        "Non-manual pool without members -> POOL_HAS_NO_ACTIVE_MEMBER",
        "MANUAL_ONLY pool without members -> manual review path, not blocker",
        "Phase 3-5 should extract `FlowResolver` / `RuleResolver`",
    ):
        require(doc, token)

    require(changelog, "Behavior changes")
    require(changelog, "None intended")
    require(files, "PoolResolver.java")
    require(readme, "development/phase3-4-pool-resolver.md")
    require(readme, "make verify-phase3-4-pool-resolver")
    require(makefile, "verify-phase3-4-pool-resolver")
    require(makefile, "verify-phase3-4-pool-resolver.py")

    line_count = len(read(service).splitlines())
    if line_count >= 1742:
        print(f"RoutingDecisionService line count did not decrease from Phase 3-3 baseline: {line_count}", file=sys.stderr)
        sys.exit(1)

    print("Phase 3-4 pool resolver contract verified.")


if __name__ == "__main__":
    main()
