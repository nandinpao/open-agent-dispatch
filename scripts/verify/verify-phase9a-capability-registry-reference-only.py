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
SERVICE = "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java"
INTERCEPTOR = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/legacy/LegacyApiDeprecationHeadersInterceptor.java"
CONFIG = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/legacy/LegacyApiDeprecationHeadersWebConfig.java"
API_CATALOG = "docs/current/api/current-api-catalog.md"
DOC = "docs/current/architecture/capability-registry-2.md"
ADR = "docs/adr/ADR-013-capability-registry-reference-only.md"
MAKEFILE = "Makefile"

# Agent Detail has a Capability Registry 2.0 read model with explicit no-routing-gate semantics.
for token in [
    "Capability Registry 2.0",
    "NO_ROUTING_GATE",
    "No routing gate",
    "reference、search、governance",
    "certification",
    "lastReported",
]:
    require(AGENT_VIEW, token)

# Dispatch Workspace exposes advisory candidate lookup only.
for token in [
    "CapabilityCandidateLookupPanel",
    "Pool 候選能力查詢（Reference-only）",
    "CANDIDATE_SUGGESTION",
    "REFERENCE ONLY",
    "getAgentCapabilities",
    "getCapabilities('ACTIVE'",
]:
    require(DISPATCH_VIEW, token)

# Data loading includes catalog and certification evidence, not routing gates.
for token in ["capabilityCatalog", "certificationRuns", "CoreAgentCertificationRun"]:
    require(HOOK, token)

# Frontend types expose source/version/certification/reference flags.
for token in ["capabilitySource", "certificationRef", "lastReportedAt", "referenceOnly", "routingGate"]:
    require(TYPES, token)

# Backend stores reference-only metadata and prevents registry rows from being interpreted as dispatch eligibility.
for token in [
    "Phase 9A Capability Registry 2.0 query",
    "capability.setDispatchEligible(false)",
    "metadata.put(\"referenceOnly\", true)",
    "metadata.put(\"routingGate\", false)",
    "assignmentMetadata.put(\"referenceOnly\", true)",
    "assignmentMetadata.put(\"routingGate\", false)",
]:
    require(SERVICE, token)

# Capability APIs are CURRENT_SUPPORT now, not legacy deprecation surfaces.
for path in [INTERCEPTOR, CONFIG]:
    forbid(path, "/admin/capabilities/**")
    forbid(path, "/admin/agents/*/capabilities/**")

# Documentation and API catalog must make the boundary explicit.
for token in ["Capability Registry 2.0", "not a Current routing authority", "Source Flow", "Agent Pool"]:
    require(DOC, token)
for token in ["ADR-013", "reference-only", "must not", "Source Flow"]:
    require(ADR, token)
for token in ["/admin/capabilities", "CURRENT_SUPPORT", "Not a routing gate", "verify-phase9a-capability-registry-reference-only.py"]:
    require(API_CATALOG, token)

require(MAKEFILE, "verify-phase9a-capability-registry-reference-only.py")

print("Phase 9A Capability Registry 2.0 reference-only contract verified.")
