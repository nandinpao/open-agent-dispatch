#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

required = [
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts",
    "ai-event-gateway-admin-ui/components/common/HumanizedCode.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/BeginnerWorkflowStepper.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/BeginnerActionPanel.tsx",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx",
    "ai-event-gateway-admin-ui/components/tasks/BeginnerTaskFlowPanel.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentCapabilityMatrix.tsx",
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts",
    "docs/PHASE3D_BEGINNER_DISPATCH_WORKFLOW_UX.md",
]

missing = [path for path in required if not (ROOT / path).exists()]
if missing:
    for path in missing:
        print(f"[FAIL] Missing required file: {path}")
    raise SystemExit(1)

checks = {
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx": [
        "派工設定精靈",
        "Choose a task scenario",
        "核准 Agent 使用明確 Capability",
        "Send test task",
        "BeginnerWorkflowStepper",
    ],
    "ai-event-gateway-admin-ui/components/tasks/BeginnerTaskFlowPanel.tsx": [
        "Beginner Task View",
        "Agent 收到 Task 後在幹嘛",
        "IDLE 不等於派工沒有送出",
    ],
    "ai-event-gateway-admin-ui/components/agents/AgentCapabilityMatrix.tsx": [
        "Agent × Capability × Task 能力矩陣",
        "Governance 核准",
        "Runtime 回報",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/labels.ts": [
        "humanizedCodeLabel",
        "WAIT_GATEWAY_ACK",
        "Runtime 未回報正在執行任務",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts": [
        "buildCapabilityMatrix",
        "taskBeginnerHeadline",
        "recommendedBeginnerActions",
    ],
}

for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {rel} does not contain expected marker: {needle}")
            raise SystemExit(1)

# Guard against the Phase 3C regression where technical labels were the only primary UI.
wizard = (ROOT / "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx").read_text(encoding="utf-8")
if wizard.count("HumanizedCode") < 1:
    print("[FAIL] Dispatch wizard should use HumanizedCode in user-facing scenario labels")
    raise SystemExit(1)

print("[PASS] Phase 3D beginner dispatch workflow UX markers verified")
