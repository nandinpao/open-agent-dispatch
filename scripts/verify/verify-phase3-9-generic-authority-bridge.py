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
    bridge = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/legacy/GenericAuthorityBridge.java"
    doc = "docs/current/development/phase3-9-generic-authority-bridge.md"
    changelog = "docs/current/phase3-9-change-log.md"
    files = "docs/current/phase3-9-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (
        service,
        orchestrator,
        bridge,
        doc,
        changelog,
        files,
        "scripts/verify/verify-phase3-7-routing-orchestrator-facade.py",
        "scripts/verify/verify-phase3-9-generic-authority-bridge.py",
    ):
        require_file(path)

    # RoutingDecisionService remains the public facade and no longer owns bridge internals.
    for token in (
        "public class RoutingDecisionService",
        "GenericAuthorityBridge genericAuthorityBridge()",
        "new GenericAuthorityBridge(properties, dispatchCutoverService, genericAuthoritativeService,",
        "routingEvidenceBuilder, this::saveAndRecord",
        "RoutingDecisionRecord saveAndRecord(RoutingDecisionRecord decision)",
    ):
        require(service, token)
    for token in (
        "RoutingDecisionRecord decideWithGenericAuthority(",
        "GenericAuthoritativeRoutingResult result = genericAuthoritativeService.route",
        "DispatchCutoverDecision cutover;",
        "generic_dispatch_authoritative_completed",
        "generic_dispatch_authoritative_no_selection",
        "generic_dispatch_non_authoritative_held",
    ):
        forbid(service, token)

    # RoutingOrchestrator calls the bridge without changing the Current bypass path.
    for token in (
        "service.genericAuthorityBridge().decide(task, excluded, decision, service.isFlowRuleTask(task))",
        "routing_source_flow_pool_first_bypassed_generic_authority",
        "SOURCE_FLOW_POOL_IS_AUTHORITATIVE",
        "routingModel=AGENT_POOL_FIRST",
    ):
        require(orchestrator, token)

    # GenericAuthorityBridge owns legacy / generic authoritative routing behavior.
    for token in (
        "package com.opensocket.aievent.core.routing.legacy;",
        "public class GenericAuthorityBridge",
        "private final DispatchCutoverService dispatchCutoverService;",
        "private final GenericDispatchAuthoritativeService genericAuthoritativeService;",
        "private final RoutingEvidenceBuilder routingEvidenceBuilder;",
        "private final Function<RoutingDecisionRecord, RoutingDecisionRecord> decisionWriter;",
        "public RoutingDecisionRecord decide(TaskRecord task,",
        "if (task == null || !flowRuleTask)",
        "P11 generic authority is required for new Flow work and is unavailable",
        "P11 cutover decision failed closed:",
        "dispatchCutoverService.decide(task)",
        "!cutover.isAuthoritative()",
        "RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED",
        "LEGACY_CONTROL_PATH_DECOMMISSIONED",
        "genericDispatchAuthoritativeService",
    ):
        # The bridge intentionally uses the field name genericAuthoritativeService, not genericDispatchAuthoritativeService.
        if token == "genericDispatchAuthoritativeService":
            continue
        require(bridge, token)

    for token in (
        "GenericAuthoritativeRoutingResult result = genericAuthoritativeService.route(task, excluded);",
        "decision.setRoutingPolicy(RoutingPolicy.FLOW_RULE);",
        "case SELECTED ->",
        "case MANUAL_REVIEW ->",
        "case REQUIREMENT_BLOCKED, NO_CANDIDATE, ERROR ->",
        "P11 generic authority: ",
        "P11 generic authority requires manual review: ",
        "P11 generic authority fail-closed: ",
        "dispatchCutoverService.recordOutcome(task, cutover, result.requirementBlocked(), result.noCandidate(),",
        "routingEvidenceBuilder.candidateTrace(result.candidates())",
        "generic_dispatch_non_authoritative_held",
        "generic_dispatch_authoritative_no_selection",
        "generic_dispatch_authoritative_completed",
        "return save(decision);",
    ):
        require(bridge, token)

    # Documentation and Make target are part of the current baseline.
    for token in (
        "Phase 3-9: Extract GenericAuthorityBridge",
        "behavior-preserving",
        "Current Source Flow / Agent Pool path bypasses generic authority",
        "P11 generic authority required fail-closed behavior",
        "cutover recordOutcome(...) side effect",
        "generic authority candidateTrace log shape",
    ):
        require(doc, token)
    require(changelog, "None intended")
    require(files, "GenericAuthorityBridge.java")
    require(files, "RoutingDecisionService.java")
    require(files, "RoutingOrchestrator.java")
    require(readme, "development/phase3-9-generic-authority-bridge.md")
    require(readme, "make verify-phase3-9-generic-authority-bridge")
    require(makefile, "verify-phase3-9-generic-authority-bridge")
    require(makefile, "verify-phase3-9-generic-authority-bridge.py")

    service_line_count = len(read(service).splitlines())
    bridge_line_count = len(read(bridge).splitlines())
    if service_line_count >= 623:
        print(f"RoutingDecisionService line count did not decrease from Phase 3-8 baseline: {service_line_count}", file=sys.stderr)
        sys.exit(1)
    if bridge_line_count < 100:
        print(f"GenericAuthorityBridge is unexpectedly small; bridge behavior was likely not extracted: {bridge_line_count}", file=sys.stderr)
        sys.exit(1)

    print("Phase 3-9 generic authority bridge contract verified.")


if __name__ == "__main__":
    main()
