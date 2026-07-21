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
    selection_dir = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection"
    doc = "docs/current/development/phase3-2-selection-strategy-registry.md"
    changelog = "docs/current/phase3-2-change-log.md"
    files = "docs/current/phase3-2-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    required_files = [
        service,
        doc,
        changelog,
        files,
        "scripts/verify/verify-phase3-2-selection-strategy-registry.py",
        f"{selection_dir}/AgentSelectionStrategy.java",
        f"{selection_dir}/SelectionStrategyRegistry.java",
        f"{selection_dir}/SelectionStrategyContext.java",
        f"{selection_dir}/SelectionResult.java",
        f"{selection_dir}/LowestLoadSelectionStrategy.java",
        f"{selection_dir}/WeightedScoreSelectionStrategy.java",
        f"{selection_dir}/ManualOnlySelectionStrategy.java",
        f"{selection_dir}/AbstractPoolSelectionStrategy.java",
    ]
    for path in required_files:
        require_file(path)

    # Service must delegate selection concerns instead of hosting the old private helpers.
    for token in (
        "private final SelectionStrategyRegistry selectionStrategyRegistry = SelectionStrategyRegistry.createDefault();",
        "selectionStrategyRegistry.isManualOnly(selectionContext(pool))",
        "selectionStrategyRegistry.annotate(score, selectionContext)",
        "SelectionResult selectionResult = selectionStrategyRegistry.select(scores, selectionContext)",
        "scores = selectionResult.candidates()",
        "private SelectionStrategyContext selectionContext(CandidateFilterResult pool)",
    ):
        require(service, token)

    # Later Phase 3 extractions may move strategy normalization behind PoolResolver,
    # but it must still delegate to SelectionStrategyRegistry rather than reintroducing
    # hard-coded supported strategy sets.
    pool_resolver = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/pool/PoolResolver.java"
    if (ROOT / pool_resolver).is_file():
        require(pool_resolver, "selectionStrategyRegistry.supportedStrategy(pool.getSelectionStrategy())")

    for token in (
        "private Comparator<AgentCandidateScore> poolComparator",
        "private AgentCandidateScore annotatePoolScore",
        "private int weightedPoolScore",
        "private int maxPoolMemberWeight",
        "private int poolMemberPriority",
        "private int poolMemberWeight",
        "private AgentPoolRoutingMember poolMember",
        "SUPPORTED_POOL_SELECTION_STRATEGIES",
    ):
        forbid(service, token)

    # Registry must explicitly own supported strategies and fallback semantics.
    registry = f"{selection_dir}/SelectionStrategyRegistry.java"
    for token in (
        "public static final String LOWEST_LOAD = \"LOWEST_LOAD\"",
        "public static final String WEIGHTED_SCORE = \"WEIGHTED_SCORE\"",
        "public static final String MANUAL_ONLY = \"MANUAL_ONLY\"",
        "SUPPORTED_STRATEGIES = Set.of(LOWEST_LOAD, WEIGHTED_SCORE, MANUAL_ONLY)",
        "return strategies.containsKey(normalized) ? normalized : LOWEST_LOAD",
        "public SelectionResult select",
        "return new SelectionResult(strategy.strategyCode(), true, List.of())",
    ):
        require(registry, token)

    # Strategy classes must preserve Phase 1 behavior.
    lowest = f"{selection_dir}/LowestLoadSelectionStrategy.java"
    weighted = f"{selection_dir}/WeightedScoreSelectionStrategy.java"
    manual = f"{selection_dir}/ManualOnlySelectionStrategy.java"
    abstract = f"{selection_dir}/AbstractPoolSelectionStrategy.java"
    context = f"{selection_dir}/SelectionStrategyContext.java"

    for token in (
        "effectiveTaskCount",
        "thenComparing(scoreDescending)",
        "thenComparing(priorityAscending)",
        "thenComparing(weightDescending)",
    ):
        require(lowest, token)

    for token in (
        "safeBaseScore * 0.8d",
        "normalizedMemberWeight * 20.0d",
        "Math.round",
        "thenComparing(weightDescending)",
        "thenComparing(priorityAscending)",
    ):
        require(weighted, token)

    for token in (
        "public boolean manualOnly()",
        "return true",
        "MANUAL_ONLY",
    ):
        require(manual, token)

    for token in (
        "poolSelectionStrategy",
        "poolMemberPriority",
        "poolMemberWeight",
        "poolMaxMemberWeight",
        "poolBaseScore",
        "poolWeightedEffectiveScore",
        "poolWeightFormula",
        "effectiveTaskCount",
        "WEIGHTED_SCORE: round(baseScore * 0.8 + normalizedMemberWeight * 20)",
    ):
        require(abstract, token)

    for token in (
        "memberPriority",
        "memberWeight",
        "maxMemberWeight",
        "effectiveTaskCount",
        "normalizeAgentId",
    ):
        require(context, token)

    # Documentation and Make wiring.
    for token in (
        "Phase 3-2: Extract SelectionStrategyRegistry",
        "No database migration",
        "No Admin UI change",
        "Unsupported persisted strategy values continue to normalize to",
        "The Phase 1-1 formula is preserved exactly",
        "MANUAL_ONLY` remains a manual queue strategy",
        "Phase 3-3: Extract RuntimeEligibilityEvaluator",
    ):
        require(doc, token)

    require(changelog, "Behavior changes")
    require(changelog, "None intended")
    require(files, "SelectionStrategyRegistry.java")
    require(readme, "development/phase3-2-selection-strategy-registry.md")
    require(readme, "make verify-phase3-2-selection-strategy-registry")
    require(makefile, "verify-phase3-2-selection-strategy-registry")
    require(makefile, "verify-phase3-2-selection-strategy-registry.py")

    line_count = len(read(service).splitlines())
    if line_count >= 1969:
        print(f"RoutingDecisionService line count did not decrease: {line_count}", file=sys.stderr)
        sys.exit(1)

    print("Phase 3-2 selection strategy registry contract verified.")


if __name__ == "__main__":
    main()
