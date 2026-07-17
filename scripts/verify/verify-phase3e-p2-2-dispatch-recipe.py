#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

required = [
    "ai-event-gateway-admin-ui/app/testing/dispatch-recipes/page.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeEntry.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/EntityRelationshipStrip.tsx",
    "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx",
    "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx",
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentDetailView.tsx",
    "ai-event-gateway-admin-ui/components/skills/SkillRegistryConsole.tsx",
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts",
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts",
    "docs/PHASE3E_P2_2_DISPATCH_RECIPE_ENTRY.md",
]

missing = [path for path in required if not (ROOT / path).exists()]
if missing:
    for path in missing:
        print(f"[FAIL] Missing required file: {path}")
    raise SystemExit(1)

checks = {
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeEntry.tsx": [
        "建立派工方案",
        "UI-only workflow",
        "buildRecipeRelationshipSteps",
        "DispatchReadinessWizard",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-readiness/EntityRelationshipStrip.tsx": [
        "Skill 能力",
        "Agent 授權",
        "Runtime 回報",
        "Task 派工",
        "Result / Issue",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts": [
        "EntityRelationshipStep",
        "buildRecipeRelationshipSteps",
        "buildTaskRelationshipSteps",
        "buildSkillRelationshipSteps",
        "buildAgentRelationshipSteps",
        "taskQueueLane",
        "taskQueueLaneLabel",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx": [
        "taskQueueLane",
        "taskQueueLaneLabel",
        "queueLane",
        "doneRows",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx": [
        "EntityRelationshipStrip",
        "buildTaskRelationshipSteps",
        "Task 派工關係鏈",
    ],
    "ai-event-gateway-admin-ui/components/agents/AgentDetailView.tsx": [
        "EntityRelationshipStrip",
        "buildAgentRelationshipSteps",
        "Agent 派工關係鏈",
    ],
    "ai-event-gateway-admin-ui/components/skills/SkillRegistryConsole.tsx": [
        "EntityRelationshipStrip",
        "buildSkillRelationshipSteps",
        "Skill 到派工的關係鏈",
    ],
    "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx": [
        "派工方案",
        "/testing/dispatch-recipes",
        "Tasks 任務",
        "Agents 執行者",
        "Skills 能力庫",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "Phase 3E-P2.2 dispatch recipe entry and relationship strip helpers",
        "buildRecipeRelationshipSteps",
        "taskQueueLane",
    ],
}

for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {rel} does not contain expected marker: {needle}")
            raise SystemExit(1)

print("[PASS] Phase 3E-P2.2 dispatch recipe entry markers verified")
