#!/usr/bin/env python3
"""Verify Phase E-P0 scenario-first dispatch management UX hooks."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED = {
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts": [
        "buildRecipeP0RemediationPlan",
        "recipeLocalAgentRestartCommand",
        "issueTrackingReadinessDecision",
        "redmineLocalEnvCommand",
        "AGENT_CORE_CAPABILITIES",
        "AGENT_CAPABILITIES",
        "REDMINE_EXECUTOR_ENABLED=true",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx": [
        "RecipeP0RemediationPanel",
        "IssueTrackingP0Panel",
        "syncAgentApprovedSkillsAndCapabilities",
        "getAdapterActionMetadata",
        "onApproveGovernance",
        "Redmine adapter 設定檢查",
    ],
    "ai-event-gateway-admin-ui/lib/api/endpoints.ts": [
        "adapterActionsMetadata: '/api/adapter-actions/metadata'",
    ],
    "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts": [
        "getAdapterActionMetadata()",
        "CoreAdapterActionMetadata",
    ],
    "ai-event-gateway-admin-ui/lib/types/core.ts": [
        "export interface CoreAdapterActionMetadata",
        "redmineExecutorEnabled",
        "redmineEndpointConfigured",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "Phase E-P0 scenario-first dispatch management helpers",
        "buildRecipeP0RemediationPlan",
        "issueTrackingReadinessDecision",
    ],
    "docs/PHASE_E_P0_SCENARIO_FIRST_DISPATCH_MANAGEMENT.md": [
        "Scenario-first Dispatch Management",
        "一鍵核准",
        "Runtime restart command",
        "Redmine adapter",
    ],
}


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def main() -> int:
    for rel, markers in REQUIRED.items():
        path = ROOT / rel
        if not path.is_file():
            fail(f"Missing required file: {rel}")
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                fail(f"{rel} does not contain required marker: {marker}")
    print("Phase E-P0 scenario-first dispatch management verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
