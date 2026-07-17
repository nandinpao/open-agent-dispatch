#!/usr/bin/env python3
"""Verify Phase E-P1 Agent / Skill management simplification UX hooks."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED = {
    "ai-event-gateway-admin-ui/lib/agents/agentSkillManagement.ts": [
        "buildAgentSkillManagementSummary",
        "buildSkillRegistryManagementSummary",
        "missingGovernanceCapabilities",
        "missingRuntimeCapabilities",
        "dispatchableCapabilities",
    ],
    "ai-event-gateway-admin-ui/components/agents/AgentDetailView.tsx": [
        "P1 · Agent 管理摘要",
        "此 Agent 可接哪些任務？缺什麼？如何修？",
        "一鍵核准缺少的 Governance 能力",
        "Runtime 缺能力：產生 Agent restart command",
        "Advanced · Capability / Skill / Runtime Diagnostics",
        "Advanced · Remediation Workflow",
        "Advanced · Runtime Session Diagnostics",
    ],
    "ai-event-gateway-admin-ui/components/skills/SkillRegistryConsole.tsx": [
        "P1 · Skill 管理摘要",
        "這個能力能不能拿來派工？下一步做什麼？",
        "到 Agent 頁核准能力",
        "Advanced · Skill Relationship Diagnostics",
        "Advanced · Skill Version / Approval Workflow",
        "Advanced · Drift / Deprecation / Dependency Remediation",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "Phase E-P1 Agent / Skill management simplification helpers",
        "buildAgentSkillManagementSummary",
        "buildSkillRegistryManagementSummary",
    ],
    "docs/PHASE_E_P1_AGENT_SKILL_MANAGEMENT_SIMPLIFICATION.md": [
        "Agent / Skill 管理頁簡化",
        "可接任務 / 能力",
        "Advanced",
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
    print("Phase E-P1 Agent / Skill management simplification verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
