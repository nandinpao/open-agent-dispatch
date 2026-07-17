#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

checks = [
    ("TaskType RESOLUTION", "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskType.java", "RESOLUTION"),
    ("classification request", "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskClassificationRequest.java", "recommendedPoolCode"),
    ("classification result", "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskClassificationResult.java", "resolutionTaskCreated"),
    ("classification service", "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskClassificationService.java", "createResolutionTask"),
    ("classification service creates resolution", "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskClassificationService.java", "TaskType.RESOLUTION"),
    ("classification service routes child", "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskClassificationService.java", "flowRuleRoutingService.applyToTask"),
    ("classification service assigns child", "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskClassificationService.java", "taskAssignmentService.assignIfPossible"),
    ("classification controller agent api", "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskClassificationController.java", "/api/agent/tasks/{taskId}/classification-result"),
    ("classification controller internal api", "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskClassificationController.java", "/internal/tasks/{taskId}/classification-result"),
    ("admin endpoint", "ai-event-gateway-admin-ui/lib/api/endpoints.ts", "taskClassificationResult"),
    ("admin api client", "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts", "submitTaskClassificationResult"),
    ("admin request type", "ai-event-gateway-admin-ui/lib/types/core.ts", "CoreTaskClassificationRequest"),
    ("admin result type", "ai-event-gateway-admin-ui/lib/types/core.ts", "CoreTaskClassificationResult"),
    ("eventType optional", "ai-event-gateway-admin-ui/lib/types/core.ts", "eventType?: string;"),
    ("phase 32-e docs", "docs/PHASE32_E_TRIAGE_CLASSIFICATION_RESOLUTION_CONTRACT.md", "Capability remains Agent metadata only"),
]

missing = []
for label, rel, token in checks:
    path = ROOT / rel
    if not path.exists():
        missing.append(f"{label}: missing file {rel}")
        continue
    text = path.read_text(errors="ignore")
    if token not in text:
        missing.append(f"{label}: missing token {token!r} in {rel}")

# Guardrail: Phase 32-E must not add capability as a required routing gate in classification service.
service = (ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskClassificationService.java").read_text(errors="ignore")
for forbidden in ["setRequiredCapabilities(request", "requiredCapability", "CapabilityRequirementMode.EXPLICIT"]:
    if forbidden in service:
        missing.append(f"classification service must not make Capability a routing gate: found {forbidden}")

if missing:
    print("Phase 32-E triage classification / resolution contract verification failed:")
    for item in missing:
        print(f" - {item}")
    raise SystemExit(1)

print("Phase 32-E triage classification / resolution contract verified.")
