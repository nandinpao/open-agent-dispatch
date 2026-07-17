#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

required = [
    "ai-event-gateway-admin-ui/components/tasks/TaskLifecycleOperatorPanel.tsx",
    "ai-event-gateway-admin-ui/components/tasks/TaskDiagnosticsTabs.tsx",
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
    "ai-event-gateway-admin-ui/components/tasks/DispatchLifecycleStepper.tsx",
    "ai-event-gateway-admin-ui/lib/tasks/dispatchLifecycle.ts",
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts",
    "docs/PHASE3E_P1_LIFECYCLE_TIMELINE_SEPARATION.md",
]
missing = [path for path in required if not (ROOT / path).exists()]
if missing:
    for path in missing:
        print(f"[FAIL] Missing required file: {path}")
    raise SystemExit(1)

checks = {
    "ai-event-gateway-admin-ui/components/tasks/TaskLifecycleOperatorPanel.tsx": [
        "Lifecycle Stepper · Operator View",
        "目前卡在哪",
        "Timeline Event Log",
        "buildOperatorLifecycleStages",
        "lifecycleOperatorDecision",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskDiagnosticsTabs.tsx": [
        "Engineer diagnostics",
        "Timeline Event Log 與進階診斷",
        "useState",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx": [
        "TaskLifecycleOperatorPanel",
        "TaskDiagnosticsTabs",
        "buildTaskDiagnosticsTabs",
        "Timeline Event Log",
        "Raw Diagnostics",
    ],
    "ai-event-gateway-admin-ui/components/tasks/DispatchLifecycleStepper.tsx": [
        "Dispatch Lifecycle Detail",
        "工程師詳細視圖",
    ],
    "ai-event-gateway-admin-ui/lib/tasks/dispatchLifecycle.ts": [
        "OperatorLifecycleStage",
        "buildOperatorLifecycleStages",
        "lifecycleOperatorDecision",
        "等待 Agent 回覆",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "Phase 3E-P1 lifecycle/timeline separation helpers",
        "buildOperatorLifecycleStages",
        "lifecycleOperatorDecision",
    ],
}

for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {rel} does not contain expected marker: {needle}")
            raise SystemExit(1)

task_detail = (ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx").read_text(encoding="utf-8")
if "平台進階診斷與治理" in task_detail:
    print("[FAIL] TaskDetailView should not keep the old single collapsed advanced diagnostics block after P1 tabs")
    raise SystemExit(1)
if "Phase 2 Dispatch Lifecycle" in (ROOT / "ai-event-gateway-admin-ui/components/tasks/DispatchLifecycleStepper.tsx").read_text(encoding="utf-8"):
    print("[FAIL] Dispatch lifecycle UI should not expose internal phase label Phase 2")
    raise SystemExit(1)

print("[PASS] Phase 3E-P1 lifecycle/timeline separation markers verified")
