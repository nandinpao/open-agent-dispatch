#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def require(path, token):
    text = read(path)
    if token not in text:
        print(f"Missing token in {path}: {token}", file=sys.stderr)
        sys.exit(1)

def forbid(path, token):
    text = read(path)
    if token in text:
        print(f"Forbidden token in {path}: {token}", file=sys.stderr)
        sys.exit(1)

REQUEST = 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskClassificationRequest.java'
RESULT = 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskClassificationResult.java'
CONTRACT = 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskA2AClassificationFlowContract.java'
SERVICE = 'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskClassificationService.java'
CONTROLLER = 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskClassificationController.java'
TYPES = 'ai-event-gateway-admin-ui/lib/types/core.ts'
API = 'ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts'
ENDPOINTS = 'ai-event-gateway-admin-ui/lib/api/endpoints.ts'
UI = 'ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx'
DOC = 'docs/current/architecture/a2a-classification-flow.md'
ADR = 'docs/adr/ADR-018-a2a-classification-flow.md'
CATALOG = 'docs/current/api/current-api-catalog.md'
MAKEFILE = 'Makefile'

for path in [REQUEST, RESULT, CONTRACT, SERVICE, CONTROLLER, TYPES, API, ENDPOINTS, UI, DOC, ADR, CATALOG]:
    if not (ROOT / path).exists():
        print(f"Missing file: {path}", file=sys.stderr)
        sys.exit(1)

for token in [
    'parentTaskId',
    'rootTaskId',
    'correlationId',
    'classificationVersion',
    'maxA2ADepth',
    'idempotencyKey',
    'Agent submits only the classification result',
]:
    require(REQUEST, token)

for token in [
    'coreOwnedTaskCreation',
    'agentCreatedTask',
    'manualReviewRequired',
    'nextAction',
    'governanceReason',
    'cycleDetected',
    'a2aDepth',
]:
    require(RESULT, token)

for token in [
    'Phase 9F A2A Classification Flow contract',
    'coreOwnedTaskCreation',
    'agentCanCreateTask',
    'cycleDetectionRequired',
    'idempotencyRequired',
    'defaultMaxA2ADepth',
    'childTaskCreationAuthority',
    'CORE_ONLY',
    'MANUAL_REVIEW_REQUIRED',
]:
    require(CONTRACT, token)

for token in [
    'a2aClassificationFlowContract',
    'A2A_CLASSIFICATION_V1',
    'a2aClassificationIdempotencyIndex',
    'validateA2AGuard',
    'computeDepthAndDetectCycle',
    'maxA2ADepth exceeded',
    'A2A cycle detected',
    'agentCreatedTask=false',
    'coreOwnedTaskCreation=true',
    'childTaskCreationAuthority',
    'RETURN_EXISTING_CHILD_TASK',
    'MANUAL_REVIEW',
]:
    require(SERVICE, token)

for token in [
    '/admin/tasks/a2a-classification/contract',
    'TaskA2AClassificationFlowContract',
    'classificationService.a2aClassificationFlowContract()',
]:
    require(CONTROLLER, token)

for token in [
    'CoreTaskA2AClassificationFlowContract',
    'parentTaskId?: string',
    'rootTaskId?: string',
    'correlationId?: string',
    'classificationVersion?: string',
    'maxA2ADepth?: number',
    'idempotencyKey?: string',
    'coreOwnedTaskCreation?: boolean',
    'agentCreatedTask?: boolean',
]:
    require(TYPES, token)

for token in [
    'taskA2AClassificationContract',
    '/admin/tasks/a2a-classification/contract',
]:
    require(ENDPOINTS, token)
for token in [
    'getTaskA2AClassificationContract',
    'CoreTaskA2AClassificationFlowContract',
]:
    require(API, token)

for token in [
    'Core-owned A2A Classification Flow',
    'Agent 只能回傳分類結果',
    'rootTaskId',
    'classificationVersion',
    'idempotencyKey',
    'maxA2ADepth',
    'cycleDetection',
    '避免 Agent 自行形成不可控 A2A 循環',
]:
    require(UI, token)

for token in [
    'A2A Classification Flow',
    'Core validates classification result',
    'Classification Agent **never creates downstream Tasks directly**',
    'parentTaskId',
    'rootTaskId',
    'correlationId',
    'classificationVersion',
    'maxA2ADepth',
    'cycleDetection',
    'idempotencyKey',
    'GET /admin/tasks/a2a-classification/contract',
]:
    require(DOC, token)

for token in [
    'ADR-018: Core-owned A2A Classification Flow',
    'Classification Agents submit classification results only',
    'Core is the only authority that creates child / continuation Tasks',
    'idempotencyKey',
    'cyclic or overly deep A2A chains are blocked',
]:
    require(ADR, token)

for token in [
    '/admin/tasks/a2a-classification/contract',
    '/api/agent/tasks/{taskId}/classification-result',
    'CURRENT_SUPPORT',
    'verify-phase9f-a2a-classification-flow.py',
]:
    require(CATALOG, token)

require(MAKEFILE, 'verify-phase9f-a2a-classification-flow.py')

forbid(SERVICE, 'appendJson(json, "phase"')
forbid(SERVICE, '"32-E"')

# Guardrail: A2A classification must not appear as a new selection strategy or runtime eligibility gate.
for rel in [
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection',
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/eligibility',
]:
    path = ROOT / rel
    if not path.exists():
        continue
    for file in path.rglob('*.java'):
        text = file.read_text(encoding='utf-8', errors='ignore')
        if 'A2A_CLASSIFICATION' in text or 'TaskClassification' in text:
            print(f'A2A classification leaked into selection/eligibility: {file.relative_to(ROOT)}', file=sys.stderr)
            sys.exit(1)

print('Phase 9F A2A Classification Flow verified.')
