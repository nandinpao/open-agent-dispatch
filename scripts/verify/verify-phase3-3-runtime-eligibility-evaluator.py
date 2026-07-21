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
    eligibility_dir = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/eligibility"
    evaluator = f"{eligibility_dir}/RuntimeEligibilityEvaluator.java"
    result = f"{eligibility_dir}/CandidateFilterResult.java"
    scoring = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/scoring/CandidateScoringService.java"
    doc = "docs/current/development/phase3-3-runtime-eligibility-evaluator.md"
    changelog = "docs/current/phase3-3-change-log.md"
    files = "docs/current/phase3-3-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (
        service,
        evaluator,
        result,
        doc,
        changelog,
        files,
        "scripts/verify/verify-phase3-3-runtime-eligibility-evaluator.py",
    ):
        require_file(path)

    # Service must keep the Phase 3-3 evaluator boundary, even after later
    # phases move direct filter/pool calls behind PoolResolver.
    for token in (
        "import com.opensocket.aievent.core.routing.eligibility.CandidateFilterResult;",
        "import com.opensocket.aievent.core.routing.eligibility.RuntimeEligibilityEvaluator;",
        "private final RuntimeEligibilityEvaluator runtimeEligibilityEvaluator;",
        "this.runtimeEligibilityEvaluator = new RuntimeEligibilityEvaluator(properties);",
        "runtimeEligibilityEvaluator.candidateBlockingReason(enforceGlobalReasons,",
    ):
        require(service, token)

    # Phase 3-8 moves capacity-full scoring evidence into CandidateScoringService.
    # Before that phase, the token may still live in RoutingDecisionService.
    if (ROOT / scoring).is_file():
        require(scoring, "runtimeEligibilityEvaluator.isCapacityFull(agent)")
    else:
        require(service, "runtimeEligibilityEvaluator.isCapacityFull(agent)")

    for token in (
        "private CandidateFilterResult filterCandidates",
        "private boolean includeForScoring",
        "private boolean isPoisonExcluded",
        "private String poolBlocker",
        "private boolean isCapacityFull",
        "private record CandidateFilterResult",
    ):
        forbid(service, token)

    # Evaluator must own runtime filtering and blocker semantics.
    for token in (
        "public class RuntimeEligibilityEvaluator",
        "public CandidateFilterResult filterCandidates",
        "public boolean includeForScoring",
        "public boolean isPoisonExcluded",
        "public String poolBlocker",
        "public boolean isCapacityFull",
        "public String candidateBlockingReason",
        "POOL_HAS_NO_ACTIVE_MEMBER",
        "POOL_AGENT_RUNTIME_NOT_FOUND",
        "POOL_AGENT_OFFLINE",
        "POOL_AGENT_CAPACITY_FULL",
        "POOL_AGENT_BACKOFF",
        "NO_ELIGIBLE_AGENT_IN_POOL",
        "reservation_excluded",
        "poison_agent_excluded",
    ):
        require(evaluator, token)

    # CandidateFilterResult must preserve routing diagnostic/evidence accessors.
    for token in (
        "public record CandidateFilterResult",
        "reservationExcluded",
        "poisonExcluded",
        "poolBlockerCode",
        "public static CandidateFilterResult blocked",
        "public CandidateFilterResult withPool",
        "public int memberCount()",
        "public int eligibleAgentCount()",
        "public String diagnostics()",
        "excludedAfterReservationRace",
        "poisonAgentExcluded",
    ):
        require(result, token)

    for token in (
        "Phase 3-3: Extract RuntimeEligibilityEvaluator",
        "No routing behavior should change",
        "Agent runtime missing -> POOL_AGENT_RUNTIME_NOT_FOUND",
        "Agent offline / expired / error -> POOL_AGENT_OFFLINE",
        "All runtime candidates capacity-full -> POOL_AGENT_CAPACITY_FULL",
        "Poison-agent exclusion -> POOL_AGENT_BACKOFF",
        "Phase 3-4 should extract `PoolResolver`",
    ):
        require(doc, token)

    require(changelog, "Behavior changes")
    require(changelog, "None intended")
    require(files, "RuntimeEligibilityEvaluator.java")
    require(readme, "development/phase3-3-runtime-eligibility-evaluator.md")
    require(readme, "make verify-phase3-3-runtime-eligibility-evaluator")
    require(makefile, "verify-phase3-3-runtime-eligibility-evaluator")
    require(makefile, "verify-phase3-3-runtime-eligibility-evaluator.py")

    line_count = len(read(service).splitlines())
    if line_count >= 1865:
        print(f"RoutingDecisionService line count did not decrease from Phase 3-2 baseline: {line_count}", file=sys.stderr)
        sys.exit(1)

    print("Phase 3-3 runtime eligibility evaluator contract verified.")


if __name__ == "__main__":
    main()
