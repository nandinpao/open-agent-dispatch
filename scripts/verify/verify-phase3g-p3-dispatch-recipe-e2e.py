#!/usr/bin/env python3
"""Static verification for Phase 3G-P3 Dispatch Recipe E2E Test Flow."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def exists(path: str) -> Path:
    p = ROOT / path
    if not p.exists():
        fail(f"Missing required file: {path}")
    return p


def contains(path: str, text: str) -> None:
    data = exists(path).read_text(encoding="utf-8")
    if text not in data:
        fail(f"Missing required text in {path}: {text}")


def main() -> int:
    wizard = "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx"
    workflow = "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts"
    test = "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts"
    doc = "docs/PHASE3G_P3_DISPATCH_RECIPE_E2E_TEST_FLOW.md"

    for path in (wizard, workflow, test, doc):
        exists(path)

    for text in (
        "RecipeE2eStatusPanel",
        "recipeE2eGuidance",
        "recipeWorkerStartCommand",
        "getTaskRuntimeView",
        "process-result worker",
        "TASK_ACK / TASK_PROGRESS / TASK_RESULT",
        "sendResponse",
        "createdTask",
    ):
        contains(wizard, text)

    for text in (
        "DispatchRecipeE2eGuidance",
        "pickStringDeep",
        "recipeWorkerStartCommand",
        "AGENT_WORKER_MODE=process-result",
        "recipeE2eGuidance",
        "WAITING_AGENT_CALLBACK",
        "E2E_COMPLETED",
        "TASK_SUBMITTED",
    ):
        contains(workflow, text)

    for text in (
        "Phase 3G-P3 dispatch recipe E2E test flow helpers",
        "recipeWorkerStartCommand",
        "recipeE2eGuidance",
        "WAITING_AGENT_CALLBACK",
        "E2E_COMPLETED",
    ):
        contains(test, text)

    contains("scripts/verify/verify-release.py", "verify-phase3g-p3-dispatch-recipe-e2e.py")

    print("Phase 3G-P3 Dispatch Recipe E2E Test Flow verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
