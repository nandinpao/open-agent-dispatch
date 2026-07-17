#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

required = [
    "ai-event-gateway-admin-ui/app/testing/dispatch-recipes/page.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeEntry.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx",
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/EntityRelationshipStrip.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessPanel.tsx",
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts",
    "docs/PHASE3E_P3_GUIDED_DISPATCH_RECIPE_WIZARD.md",
]

missing = [path for path in required if not (ROOT / path).exists()]
if missing:
    for path in missing:
        print(f"[FAIL] Missing required file: {path}")
    raise SystemExit(1)

checks = {
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeEntry.tsx": [
        "DispatchRecipeWizard",
        "PHASE_3E_P3",
        "選場景 → 確認 Skill → 選 Agent → 檢查 readiness → 產生測試 payload → 查看 Task Console",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx": [
        "Guided Dispatch Recipe Wizard",
        "RecipeStepRail",
        "ScenarioCard",
        "DispatchReadinessPanel",
        "recipeTestEventPayload",
        "recipeCurlCommand",
        "直接送出測試事件",
        "進階診斷 · 派工方案 request / result",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts": [
        "DispatchRecipeScenario",
        "fallbackRecipeScenarios",
        "normalizeDispatchRecipeScenarios",
        "scenarioToRecipeRequest",
        "recipeTestEventPayload",
        "recipeCurlCommand",
        "buildRecipeWizardSteps",
        "recipeDecision",
        "pickRecipeTaskId",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "Phase 3E-P3 guided dispatch recipe wizard helpers",
        "normalizeDispatchRecipeScenarios",
        "buildRecipeWizardSteps",
        "recipeTestEventPayload",
        "recipeDecision",
        "pickRecipeTaskId",
    ],
    "docs/PHASE3E_P3_GUIDED_DISPATCH_RECIPE_WIZARD.md": [
        "Phase 3E-P3",
        "Guided Dispatch Recipe Wizard",
        "UI-only",
        "不新增 dispatch_recipe table",
        "Step 1",
        "Step 6",
    ],
}

for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {rel} does not contain expected marker: {needle}")
            raise SystemExit(1)

print("[PASS] Phase 3E-P3 guided dispatch recipe wizard markers verified")
