#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

required = [
    "ai-event-gateway-admin-ui/components/common/DecisionHeader.tsx",
    "ai-event-gateway-admin-ui/components/common/RawDiagnosticsPanel.tsx",
    "ai-event-gateway-admin-ui/components/common/StatusBadge.tsx",
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts",
    "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx",
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentDetailView.tsx",
    "ai-event-gateway-admin-ui/components/skills/SkillRegistryConsole.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx",
    "docs/PHASE3E_P0_DECISION_ORIENTED_UI.md",
]
missing = [path for path in required if not (ROOT / path).exists()]
if missing:
    for path in missing:
        print(f"[FAIL] Missing required file: {path}")
    raise SystemExit(1)

checks = {
    "ai-event-gateway-admin-ui/components/common/DecisionHeader.tsx": [
        "目前狀態",
        "卡住原因 / 判斷",
        "下一步",
        "DecisionHeaderAction",
    ],
    "ai-event-gateway-admin-ui/components/common/RawDiagnosticsPanel.tsx": [
        "details",
        "工程師原始資料",
        "JsonViewer",
    ],
    "ai-event-gateway-admin-ui/components/common/StatusBadge.tsx": [
        "beginnerStatusLabel",
        "beginnerStatusDescription",
        "code:",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts": [
        "taskDecisionSummary",
        "agentDecisionSummary",
        "skillDecisionSummary",
        "DecisionSummary",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx": [
        "Task queue decision header",
        "Open Failure Queue",
        "搜尋 Task ID、Issue、Agent、事件、錯誤訊息",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx": [
        "Task decision header",
        "RawDiagnosticsPanel",
        "TaskDiagnosticsTabs",
    ],
    "ai-event-gateway-admin-ui/components/agents/AgentDetailView.tsx": [
        "Agent decision header",
        "Check Dispatch Readiness",
        "RawDiagnosticsPanel",
    ],
    "ai-event-gateway-admin-ui/components/skills/SkillRegistryConsole.tsx": [
        "Skill decision header",
        "Skill Registry 能力目錄",
        "進階 Metadata JSON",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx": [
        "Dispatch setup wizard",
        "DecisionHeader",
        "檢查是否可以派工",
    ],
}

for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {rel} does not contain expected marker: {needle}")
            raise SystemExit(1)

skill_text = (ROOT / "ai-event-gateway-admin-ui/components/skills/SkillRegistryConsole.tsx").read_text(encoding="utf-8")
if "P9." in skill_text or "P9.1" in skill_text or "P9.5" in skill_text:
    print("[FAIL] Skill Registry UI should not expose internal phase labels such as P9.x")
    raise SystemExit(1)

agent_text = (ROOT / "ai-event-gateway-admin-ui/components/agents/AgentDetailView.tsx").read_text(encoding="utf-8")
task_text = (ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx").read_text(encoding="utf-8")
if "<JsonViewer" in agent_text or "<JsonViewer" in task_text:
    print("[FAIL] Agent/Task detail should route raw JSON through collapsed RawDiagnosticsPanel")
    raise SystemExit(1)

print("[PASS] Phase 3E-P0 decision-oriented UI markers verified")
