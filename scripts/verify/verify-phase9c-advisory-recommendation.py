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

SERVICE = "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java"
CONTROLLER = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java"
MODEL = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAdvisoryRecommendation.java"
COMMAND = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAdvisoryRecommendationDecisionCommand.java"
TYPES = "ai-event-gateway-admin-ui/lib/types/core.ts"
API = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
ENDPOINTS = "ai-event-gateway-admin-ui/lib/api/endpoints.ts"
DISPATCH_VIEW = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx"
DOC = "docs/current/architecture/advisory-recommendation.md"
ADR = "docs/adr/ADR-015-advisory-recommendation-only.md"
CATALOG = "docs/current/api/current-api-catalog.md"
MAKEFILE = "Makefile"

for token in [
    "advisoryOnly",
    "autoApply",
    "routingImpact",
    "evidenceWindow",
    "confidence",
    "suggestedChange",
    "decisionAudit",
    "acceptedBy",
    "rejectedBy",
]:
    require(MODEL, token)

for token in ["operatorId", "reason", "metadata"]:
    require(COMMAND, token)

for token in [
    "Phase 9C Advisory Recommendation",
    "advisory-only",
    "must not directly mutate",
    "searchAdvisoryRecommendations",
    "generateAdvisoryRecommendations",
    "acceptAdvisoryRecommendation",
    "rejectAdvisoryRecommendation",
    "ADD_AGENT_TO_POOL",
    "ADJUST_AGENT_WEIGHT",
    "POOL_CAPACITY_RISK",
    "INCREASE_POOL_CAPACITY",
    "configurationIntent",
    "REVIEW_REQUIRED",
    "autoApply", "false",
    "routingImpact", "NONE",
]:
    require(SERVICE, token)

for token in [
    "/recommendations/advisory",
    "/recommendations/advisory/generate",
    "/recommendations/advisory/{recommendationId}/accept",
    "/recommendations/advisory/{recommendationId}/reject",
]:
    require(CONTROLLER, token)

for token in [
    "CoreAgentAdvisoryRecommendation",
    "CoreAgentAdvisoryRecommendationDecisionCommand",
    "advisoryOnly",
    "autoApply",
    "routingImpact",
    "suggestedChange",
    "decisionAudit",
]:
    require(TYPES, token)

for token in [
    "getAdvisoryRecommendations",
    "generateAdvisoryRecommendations",
    "acceptAdvisoryRecommendation",
    "rejectAdvisoryRecommendation",
]:
    require(API, token)

for token in [
    "advisoryRecommendations",
    "advisoryRecommendationsGenerate",
    "advisoryRecommendationAccept",
    "advisoryRecommendationReject",
]:
    require(ENDPOINTS, token)

for token in [
    "Advisory Recommendation",
    "建議中心（只建議，不自動修改）",
    "Accept 只留下 configuration review intent",
    "autoApply=false",
    "routingImpact",
    "Accept",
    "Reject",
    "generateAdvisoryRecommendations",
]:
    require(DISPATCH_VIEW, token)

for token in [
    "Advisory Recommendation",
    "advisory-only",
    "autoApply=false",
    "routingImpact=NONE",
    "REVIEW_REQUIRED",
    "must not directly mutate",
]:
    require(DOC, token)
    require(ADR, token)

for token in [
    "/admin/recommendations/advisory",
    "Phase 9C",
    "advisoryOnly=true",
    "autoApply=false",
    "routingImpact=NONE",
    "verify-phase9c-advisory-recommendation.py",
]:
    require(CATALOG, token)

require(MAKEFILE, "verify-phase9c-advisory-recommendation.py")

# Guardrails: advisory recommendations must not leak into routing or eligibility implementation.
for path in [
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/routing/selection/CandidateScoringService.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/routing/eligibility/RuntimeEligibilityEvaluator.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
]:
    target = ROOT / path
    if target.exists():
        text = target.read_text(encoding="utf-8")
        for token in ["AgentAdvisoryRecommendation", "advisoryRecommendations", "recommendationType", "suggestedChange"]:
            if token in text:
                raise AssertionError(f"Phase 9C advisory recommendation leaked into Current routing path: {path} contains {token}")

print("Phase 9C Advisory Recommendation advisory-only contract verified.")
