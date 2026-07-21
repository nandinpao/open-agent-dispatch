#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"Missing required file: {path}")
    return target.read_text(encoding="utf-8")

def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        raise AssertionError(f"Expected token not found in {path}: {token}")

def forbid(path: str, token: str) -> None:
    text = read(path)
    if token in text:
        raise AssertionError(f"Forbidden token found in {path}: {token}")

AGENT_VIEW = "ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx"
DISPATCH_VIEW = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx"
HOOK = "ai-event-gateway-admin-ui/hooks/useAgentDetail.ts"
TYPES = "ai-event-gateway-admin-ui/lib/types/core.ts"
CONTROLLER = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java"
SERVICE = "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java"
MODEL = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentQualityMetricsWindow.java"
API = "docs/current/api/current-api-catalog.md"
DOC = "docs/current/architecture/agent-quality-observation.md"
ADR = "docs/adr/ADR-014-agent-quality-observation-only.md"
MAKEFILE = "Makefile"

for token in [
    "Agent Quality Observation",
    "Observation-only / no selection impact",
    "Success Rate",
    "Avg Completion",
    "P95 Completion",
    "ACK Timeout",
    "Result Failure",
    "Retry Rate",
    "Manual Reassign",
    "responsibilityScope",
    "selectionImpact=NONE",
    "observationOnly=true",
]:
    require(AGENT_VIEW, token)

for token in [
    "Agent Quality Observation",
    "getAgentQualityWindows",
    "qualityByAgent",
    "qualityObservationSummary",
    "Observation-only",
    "does not change Selection Strategy",
    "selectionImpact=NONE",
]:
    require(DISPATCH_VIEW, token)

for token in ["agentQualityWindows", "getAgentQualityWindows", "CoreAgentQualityMetricsWindow"]:
    require(HOOK, token)

for token in [
    "p95CompletionLatencyMs",
    "ackTimeoutRate",
    "resultFailureRate",
    "retryRate",
    "manualReassignmentRate",
    "recentHealthScore",
    "observationWindow",
    "minimumSample",
    "decayWindow",
    "responsibilityScope",
    "observationOnly",
    "selectionImpact",
]:
    require(TYPES, token)
    require(MODEL, token)

for token in [
    "/quality/agents/{agentId}/windows",
    "findAgentQualityWindows",
    "upsertAgentQualityWindow",
    "/quality/supply-profiles",
]:
    require(CONTROLLER, token)

for token in [
    "Phase 9B Agent Quality Observation",
    "observation-only evidence",
    "must not feed",
    "Selection Strategy",
    "Runtime Eligibility",
    "metadata.put(\"observationOnly\", true)",
    "metadata.put(\"selectionImpact\", \"NONE\")",
]:
    require(SERVICE, token)

for token in [
    "Agent Quality Observation",
    "observation-only",
    "selectionImpact=NONE",
    "responsibilityScope",
    "minimumSample",
]:
    require(DOC, token)
    require(ADR, token)

for token in [
    "/admin/quality/agents/{agentId}/windows",
    "Phase 9B Agent Quality Observation",
    "verify-phase9b-agent-quality-observation.py",
]:
    require(API, token)

require(MAKEFILE, "verify-phase9b-agent-quality-observation.py")

# Guardrails: quality may be observed but not added to Current routing / eligibility package names.
for path in [
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/routing/selection/CandidateScoringService.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/routing/eligibility/RuntimeEligibilityEvaluator.java",
]:
    target = ROOT / path
    if target.exists():
        text = target.read_text(encoding="utf-8")
        forbidden = ["AgentQualityMetricsWindow", "recentHealthScore", "qualityObservation", "selectionImpact"]
        for token in forbidden:
            if token in text:
                raise AssertionError(f"Phase 9B quality observation leaked into routing/eligibility path: {path} contains {token}")

print("Phase 9B Agent Quality Observation observation-only contract verified.")
