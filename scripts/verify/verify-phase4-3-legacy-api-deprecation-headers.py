#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"Missing required file: {path}")
    return p.read_text(encoding="utf-8")


def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        raise AssertionError(f"Expected token not found in {path}: {token}")


def forbid(path: str, token: str) -> None:
    text = read(path)
    if token in text:
        raise AssertionError(f"Forbidden token found in {path}: {token}")


INTERCEPTOR = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/legacy/LegacyApiDeprecationHeadersInterceptor.java"
CONFIG = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/legacy/LegacyApiDeprecationHeadersWebConfig.java"
DOC = "docs/archive/phase-series/current-history/development/phase4-3-legacy-api-deprecation-headers.md"
INVENTORY = "docs/archive/phase-series/current-history/development/phase4-2-current-path-legacy-dependency-inventory.md"
MAKEFILE = "Makefile"

# Core implementation exists.
require(INTERCEPTOR, "class LegacyApiDeprecationHeadersInterceptor")
require(CONFIG, "class LegacyApiDeprecationHeadersWebConfig")
require(CONFIG, "addInterceptors")
require(INTERCEPTOR, "HandlerInterceptor")
require(INTERCEPTOR, "preHandle")

# Header contract.
for token in [
    'response.setHeader("Deprecation", "true")',
    'response.setHeader("Link", "<" + route.replacementPath() + ">; rel=\\"successor-version\\"")',
    'response.setHeader("X-OpenDispatch-Legacy-Category", route.legacyCategory())',
    'response.setHeader("X-OpenDispatch-Legacy-Status", DEPRECATION_STATUS)',
    'response.setHeader("X-OpenDispatch-Replacement", route.replacementDescription())',
    'response.setHeader("X-OpenDispatch-Current-Model", CURRENT_MODEL)',
    'SOURCE_FLOW_AGENT_POOL',
    'DEPRECATED_LEGACY_ROUTING_API',
]:
    require(INTERCEPTOR, token)

# Route coverage from Phase 4-2 legacy candidate inventory. Capability registry APIs were promoted to CURRENT_SUPPORT in Phase 9A and are intentionally excluded.
for route in [
    "/admin/dispatch-contracts/**",
    "/admin/dispatch-contract/**",
    "/admin/dispatch-policies/**",
    "/admin/dispatch-governance/cutover/**",
    "/admin/agents/*/dispatch-eligibility",
    "/admin/tasks/*/dispatch-requirements",
    "/admin/tasks/*/eligible-agents",
    "/admin/tasks/*/eligible-agents-v2",
    "/admin/enforce/legacy-final-report",
]:
    require(INTERCEPTOR, route)
    require(CONFIG, route)
    require(DOC, route)

# Replacement categories must be explicit and stable.
for category in [
    "dispatch-contract",
    "assignment-profile",
    "cutover",
    "governance-eligibility-diagnostic",
    "legacy-final-report",
]:
    require(INTERCEPTOR, category)
    require(DOC, category)

# Controller surfaces from the Phase 4-2 plan remain documented.
for controller in [
    "DispatchContractController",
    "AgentAssignmentController",
    "DispatchCutoverController",
    "DispatchEligibilityController",
    "EnforceOperationsController",
]:
    require(DOC, controller)

# Phase 4-2 inventory remains present and still carries replacement framing.
require(INVENTORY, "Deprecated API / replacement inventory")
require(INVENTORY, "/admin/dispatch-contracts/test-task")
require(INVENTORY, "/admin/tasks/{taskId}/eligible-agents*")

# Make target is chained from Phase 4-2.
require(MAKEFILE, "verify-phase4-3-legacy-api-deprecation-headers")
require(MAKEFILE, "verify-phase4-2-current-path-legacy-dependency-inventory")

# Phase 4-3 must not add a new Flyway migration.
migration_names = [p.name for p in (ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration").glob("V*__*.sql")]
if any(name.startswith("V7__") for name in migration_names):
    raise AssertionError("Phase 4-3 must not introduce a V7 Flyway migration")

print("Phase 4-3 legacy API deprecation headers contract verified.")
