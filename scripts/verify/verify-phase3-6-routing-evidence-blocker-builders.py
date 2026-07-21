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
    evidence = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/evidence/RoutingEvidenceBuilder.java"
    blocker = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/evidence/RoutingBlockerResolver.java"
    bridge = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/legacy/GenericAuthorityBridge.java"
    doc = "docs/current/development/phase3-6-routing-evidence-blocker-builders.md"
    changelog = "docs/current/phase3-6-change-log.md"
    files = "docs/current/phase3-6-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (
        service,
        orchestrator,
        evidence,
        blocker,
        bridge,
        doc,
        changelog,
        files,
        "scripts/verify/verify-phase3-6-routing-evidence-blocker-builders.py",
    ):
        require_file(path)

    # RoutingDecisionService delegates evidence and blocker responsibility.
    for token in (
        "import com.opensocket.aievent.core.routing.evidence.RoutingBlockerResolver;",
        "import com.opensocket.aievent.core.routing.evidence.RoutingEvidenceBuilder;",
        "private final RoutingEvidenceBuilder routingEvidenceBuilder = new RoutingEvidenceBuilder();",
        "private final RoutingBlockerResolver routingBlockerResolver = new RoutingBlockerResolver();",
        "routingEvidenceBuilder()",
        "routingBlockerResolver()",
        "routingBlockerResolver.observationBlockingReasonCode(decision)",
    ):
        require(service, token)
    for token in (
        "service.routingEvidenceBuilder().manualOnlyDecisionReason(candidatePool, service.flowRuleDecisionSuffix(task))",
        "service.routingBlockerResolver().noCandidateError(task, candidatePool)",
        "service.routingBlockerResolver().belowMinimumError(task, selected, candidatePool, service.properties())",
        "service.routingEvidenceBuilder().selectedDecisionReason(",
    ):
        require(orchestrator, token)

    for token in (
        "private DispatchUserFacingError userFacingNoCandidateError",
        "private String poolFirstNoCandidateMessage",
        "private String poolFirstNoCandidateAction",
        "private DispatchUserFacingError userFacingBelowMinimumError",
        "private DispatchUserFacingError userFacingBackendEligibilityError",
        "private String firstBackendDispatchCode",
        "private String blockingReasonCode",
    ):
        forbid(service, token)

    # RoutingEvidenceBuilder owns stable decision reason and trace assembly.
    for token in (
        "public final class RoutingEvidenceBuilder",
        "public List<Map<String, Object>> candidateTrace(List<AgentCandidateScore> candidates)",
        "trace.put(\"agentId\", candidate.agentId())",
        "trace.put(\"score\", candidate.score())",
        "trace.put(\"status\", candidate.status())",
        "trace.put(\"matchedCapabilities\", candidate.matchedCapabilities())",
        "trace.put(\"missingCapabilities\", candidate.missingCapabilities())",
        "trace.put(\"reason\", candidate.reason())",
        "trace.put(\"scoreBreakdown\", candidate.scoreBreakdown())",
        "public String manualOnlyDecisionReason(CandidateFilterResult candidatePool, String flowRuleDecisionSuffix)",
        "MANUAL_ASSIGNMENT_REQUIRED: Agent Pool",
        "uses MANUAL_ONLY; no automatic Agent assignment or Netty delivery will be created",
        "public String selectedDecisionReason(RoutingPolicy policy,",
        "Selected by",
    ):
        require(evidence, token)

    # RoutingBlockerResolver owns user-facing no-candidate/below-minimum diagnostics.
    for token in (
        "public final class RoutingBlockerResolver",
        "public DispatchUserFacingError noCandidateError(TaskRecord task, CandidateFilterResult candidatePool)",
        "DispatchUserFacingErrorCode.DISPATCH_NO_AGENT_ONLINE",
        "目標 Agent Pool 沒有啟用中的成員。",
        "目標 Agent Pool 的成員尚未建立 runtime binding。",
        "目標 Agent Pool 的成員目前離線或心跳不可用。",
        "目標 Agent Pool 的成員容量已滿。",
        "目標 Agent Pool 的成員暫時被 backoff 排除。",
        "Flow Rule 指定的 target Pool 不存在或未啟用。",
        "public DispatchUserFacingError belowMinimumError(TaskRecord task,",
        "DispatchUserFacingErrorCode.DISPATCH_SCORE_BELOW_THRESHOLD",
        "technicalBelowMinimum",
        "scoreBreakdown",
        "public String observationBlockingReasonCode(RoutingDecisionRecord decision)",
        "manual_review_required",
        "assignment_disabled",
    ):
        require(blocker, token)

    require(bridge, "routingEvidenceBuilder.candidateTrace(result.candidates())")

    for token in (
        "Phase 3-6: Extract RoutingEvidenceBuilder / RoutingBlockerResolver",
        "behavior-preserving",
        "NO_CANDIDATE user-facing error code remains DISPATCH_NO_AGENT_ONLINE",
        "Pool blocker messages remain unchanged",
        "scoreBreakdown key remains present",
        "Phase 3-7 should introduce a `RoutingOrchestrator` facade",
    ):
        require(doc, token)

    require(changelog, "None intended")
    require(files, "RoutingEvidenceBuilder.java")
    require(files, "RoutingBlockerResolver.java")
    require(readme, "development/phase3-6-routing-evidence-blocker-builders.md")
    require(readme, "make verify-phase3-6-routing-evidence-blocker-builders")
    require(makefile, "verify-phase3-6-routing-evidence-blocker-builders")
    require(makefile, "verify-phase3-6-routing-evidence-blocker-builders.py")

    line_count = len(read(service).splitlines())
    if line_count >= 1595:
        print(f"RoutingDecisionService line count did not decrease from Phase 3-5 baseline: {line_count}", file=sys.stderr)
        sys.exit(1)

    print("Phase 3-6 routing evidence/blocker builder contract verified.")


if __name__ == "__main__":
    main()
