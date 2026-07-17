#!/usr/bin/env python3
"""Verify P2-B Core business observation consolidation."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        fail(f"Missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: str, markers: list[str]) -> str:
    text = read(rel)
    for marker in markers:
        if marker not in text:
            fail(f"{rel} is missing required marker: {marker}")
    return text


def forbid(rel: str, markers: list[str]) -> None:
    text = read(rel)
    for marker in markers:
        if marker in text:
            fail(f"{rel} contains forbidden marker: {marker}")


def assert_cardinality(rel: str) -> None:
    text = read(rel)
    if "public enum LowCardinalityKeyNames" not in text or "public enum HighCardinalityKeyNames" not in text:
        fail(f"{rel} does not declare both cardinality contracts")
    low = text.split("public enum LowCardinalityKeyNames", 1)[1].split("public enum HighCardinalityKeyNames", 1)[0]
    for unbounded in ["tenant.id", "task.id", "flow.id", "rule.id", "agent.id", "requested.skill", "decision.id", "source.system", "event.type"]:
        if unbounded in low:
            fail(f"Unbounded field leaked into low-cardinality keys in {rel}: {unbounded}")


def main() -> int:
    flow = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java"
    require(flow, [
        "ObservationRegistry observationRegistry",
        "FlowRuleRoutingObservationDocumentation.FLOW_RULE_RESOLUTION",
        "observation.observe(() ->",
        "LowCardinalityKeyNames.BLOCKING_REASON_CODE",
        "HighCardinalityKeyNames.FLOW_ID",
        "HighCardinalityKeyNames.RULE_ID",
        "HighCardinalityKeyNames.REQUESTED_SKILL",
        "ResolutionOrigin.EXISTING",
        "ResolutionOrigin.DATABASE",
        '"no_active_flow_rule"',
    ])
    forbid(flow, ["io.micrometer.tracing", "Tracer", "Span", "nextSpan(", "withSpan("])

    eligibility = "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java"
    require(eligibility, [
        "ObservationRegistry observationRegistry",
        "DispatchEligibilityObservationDocumentation.AGENT_EVALUATION",
        "DispatchEligibilityObservationDocumentation.CANDIDATE_SELECTION",
        "evaluateAgentObserved",
        "eligibleAgentsObserved",
        "firstBlockingReasonCode",
        "firstBlockedCandidateCode",
        "LowCardinalityKeyNames.REQUIREMENT_RESOLUTION",
        "HighCardinalityKeyNames.AGENT_ID",
    ])
    forbid(eligibility, ["io.micrometer.tracing", "nextSpan(", "withSpan("])

    routing = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java"
    require(routing, [
        "RoutingObservationDocumentation.ASSIGNMENT_DECISION",
        "RoutingObservationDocumentation.CANDIDATE_SELECTION",
        "decideObserved",
        "selectCandidates",
        "recordAssignmentDecision",
        "candidateBlockingReason",
        "LowCardinalityKeyNames.ASSIGNMENT_STATUS",
        "HighCardinalityKeyNames.SELECTED_AGENT_ID",
        "RoutingCandidateSelection",
    ])
    forbid(routing, ["io.micrometer.tracing", "nextSpan(", "withSpan("])

    docs = [
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/observation/FlowRuleRoutingObservationDocumentation.java",
        "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/observation/DispatchEligibilityObservationDocumentation.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/observation/RoutingObservationDocumentation.java",
    ]
    for rel in docs:
        require(rel, ["implements ObservationDocumentation", "LowCardinalityKeyNames", "HighCardinalityKeyNames", "dispatch.blocking.reason_code"])
        assert_cardinality(rel)

    require("ai-event-gateway-core/agent-control/pom.xml", ["<artifactId>micrometer-observation</artifactId>"])
    task_pom = require("ai-event-gateway-core/task-orchestration/pom.xml", ["<artifactId>micrometer-observation</artifactId>"])
    if "<artifactId>micrometer-tracing</artifactId>" in task_pom:
        fail("task-orchestration must not depend directly on micrometer-tracing after P2-B")

    core_root = ROOT / "ai-event-gateway-core"
    violations: list[str] = []
    for source in core_root.rglob("src/main/java/**/*.java"):
        text = source.read_text(encoding="utf-8")
        if "io.micrometer.tracing.Tracer" in text or "io.micrometer.tracing.Span" in text:
            violations.append(str(source.relative_to(ROOT)))
    if violations:
        fail("Core business source still directly imports Tracer/Span: " + ", ".join(violations))

    tests = {
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingObservationTest.java": [
            "dispatch.flow_rule.resolve", "no_active_flow_rule", "RecordingHandler"
        ],
        "ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityObservationTest.java": [
            "dispatch.eligibility.evaluate", "agent_profile_exists", "RecordingHandler"
        ],
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/RoutingBusinessObservationTest.java": [
            "dispatch.routing.candidates", "dispatch.assignment.decide", "no_candidate", "RecordingHandler"
        ],
    }
    for rel, markers in tests.items():
        require(rel, markers)

    require("docs/architecture/P2-B_CORE_BUSINESS_OBSERVATION_CONSOLIDATION.md", [
        "dispatch.flow_rule.resolve",
        "dispatch.eligibility.evaluate",
        "dispatch.routing.candidates",
        "dispatch.assignment.decide",
        "High-cardinality evidence is trace-only",
        "P3 owns cross-thread and protocol propagation",
    ])

    print("P2-B Core business observation verification passed.")
    print("- Flow Rule resolution uses ObservationDocumentation")
    print("- Eligibility and candidate discovery expose bounded blocking codes")
    print("- Runtime candidate selection and final assignment decision are observed")
    print("- task/flow/rule/agent/requestedSkill remain high-cardinality")
    print("- Core production source contains no direct Tracer/Span imports")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
