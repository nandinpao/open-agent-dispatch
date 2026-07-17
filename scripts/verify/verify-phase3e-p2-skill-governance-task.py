#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

required = [
    "ai-event-gateway-admin-ui/components/dispatch-readiness/CapabilityResolutionMatrix.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessPanel.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentCapabilityMatrix.tsx",
    "ai-event-gateway-admin-ui/components/skills/SkillRelationshipPanel.tsx",
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx",
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts",
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts",
    "docs/PHASE3E_P2_SKILL_GOVERNANCE_TASK_RELATIONSHIP.md",
]
missing = [path for path in required if not (ROOT / path).exists()]
if missing:
    for path in missing:
        print(f"[FAIL] Missing required file: {path}")
    raise SystemExit(1)

checks = {
    "ai-event-gateway-admin-ui/components/dispatch-readiness/CapabilityResolutionMatrix.tsx": [
        "Task → Skill → Governance → Runtime",
        "Capability Resolution Matrix",
        "結論與下一步",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessPanel.tsx": [
        "Dispatch Readiness 判斷鏈",
        "buildDispatchReadinessChain",
        "readinessChainSummary",
        "Task、Skill Registry、Agent Governance、Runtime Agent",
    ],
    "ai-event-gateway-admin-ui/components/agents/AgentCapabilityMatrix.tsx": [
        "CapabilityResolutionMatrix",
        "Agent × Skill × Task 能力矩陣",
    ],
    "ai-event-gateway-admin-ui/components/skills/SkillRelationshipPanel.tsx": [
        "Skill / Agent / Task 關聯入口",
        "Skill Registry 只定義",
        "Dispatch Setup Wizard",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx": [
        "CapabilityResolutionMatrix",
        "Task 需要能力與 Agent 關係",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx": [
        "DispatchReadinessPanel",
        "Task → Skill → Governance → Runtime 判斷鏈",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts": [
        "DispatchReadinessChainStep",
        "buildDispatchReadinessChain",
        "readinessChainSummary",
        "Task 需要能力",
        "Runtime 回報能力",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "Phase 3E-P2 skill/governance/runtime/task relationship helpers",
        "buildDispatchReadinessChain",
        "readinessChainSummary",
    ],
}

for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {rel} does not contain expected marker: {needle}")
            raise SystemExit(1)

wizard = (ROOT / "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx").read_text(encoding="utf-8")
if "DispatchReadinessPanel" not in wizard:
    print("[FAIL] DispatchReadinessWizard should use the reusable DispatchReadinessPanel")
    raise SystemExit(1)

print("[PASS] Phase 3E-P2 skill/governance/runtime/task relationship markers verified")
