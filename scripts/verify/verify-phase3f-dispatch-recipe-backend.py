#!/usr/bin/env python3
"""Verify Phase 3F Dispatch Recipe backend model and task capability resolver."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipe.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipeRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipeEvaluationRequest.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipeEvaluationResult.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolveRequest.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolveResult.java",
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/InMemoryDispatchRecipeRepository.java",
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipeService.java",
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchRecipeController.java",
    "ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverServiceTest.java",
    "ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipeServiceTest.java",
    "docs/PHASE3F_DISPATCH_RECIPE_BACKEND.md",
]

REQUIRED_SNIPPETS = [
    ("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchRecipeController.java", "/admin/dispatch-recipes"),
    ("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchRecipeController.java", "/admin/task-capabilities/resolve"),
    ("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java", "NO_CONFIGURED_DISPATCH_CONTRACT"),
    ("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java", "tenantId = requireNonBlank"),
    ("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java", "sourceSystem = requireNonBlank"),
    ("ai-event-gateway-admin-ui/lib/api/endpoints.ts", "dispatchRecipes"),
    ("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts", "getDispatchRecipeTemplates"),
    ("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts", "evaluateDispatchRecipe"),
    ("ai-event-gateway-admin-ui/lib/types/core.ts", "CoreDispatchRecipe"),
    ("ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx", "getDispatchRecipeTemplates"),
]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def main() -> int:
    for relative in REQUIRED_FILES:
        path = ROOT / relative
        if not path.is_file():
            fail(f"Missing required file: {relative}")
    for relative, needle in REQUIRED_SNIPPETS:
        path = ROOT / relative
        text = path.read_text(encoding="utf-8")
        if needle not in text:
            fail(f"Missing expected snippet {needle!r} in {relative}")

    resolver_text = (ROOT / "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java").read_text(encoding="utf-8")
    for token in ("MES_ALARM_TRIAGE", "ERP_PURCHASE_ORDER_REVIEW", "HR_PAYROLL_ANOMALY_ANALYSIS", "fallback=INCIDENT_ANALYSIS"):
        if token in resolver_text:
            fail(f"Source-specific resolver fallback remains: {token}")

    forbidden = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V3F__dispatch_recipe.sql"
    if forbidden.exists():
        fail("Phase 3F should stay repository/API skeleton only; unexpected DB migration found.")

    print("[OK] Phase 3F dispatch recipe backend model and task capability resolver verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
