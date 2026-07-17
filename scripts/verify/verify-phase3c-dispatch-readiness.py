#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

required = [
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationRequest.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationResult.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessCheck.java",
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationService.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchReadinessController.java",
    "ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationServiceTest.java",
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/labels.ts",
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx",
    "ai-event-gateway-admin-ui/app/testing/dispatch-readiness/page.tsx",
    "ai-event-gateway-admin-ui/tests/dispatch-readiness-labels.test.ts",
    "docs/PHASE3C_DISPATCH_READINESS_BEGINNER_UI.md",
]

missing = [path for path in required if not (ROOT / path).exists()]
if missing:
    for path in missing:
        print(f"[FAIL] Missing required file: {path}")
    raise SystemExit(1)

checks = {
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchReadinessController.java": [
        "/admin/dispatch-readiness/evaluate",
        "/admin/dispatch-readiness/templates",
    ],
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationService.java": [
        "CAPABILITY_DEFINED",
        "GOVERNANCE_APPROVED_CAPABILITY",
        "RUNTIME_REPORTED_CAPABILITY",
        "CAPABILITY_CONTRACT_ELIGIBLE",
        "setBeginnerSummary",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx": [
        "Source System is required",
        "建立/更新進階政策定義",
        "派工準備度 Checklist",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/labels.ts": [
        "TASK_EXECUTION",
        "GENERAL_AGENT",
        "CAPABILITY_CONTRACT_ELIGIBLE",
    ],
}

for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {rel} does not contain expected marker: {needle}")
            raise SystemExit(1)

print("[PASS] Phase 3C dispatch readiness beginner API/UI markers verified")
