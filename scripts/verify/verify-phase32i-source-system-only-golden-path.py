#!/usr/bin/env python3
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"Missing required file: {path}")
    return target.read_text(encoding="utf-8")

def require(path: str, *needles: str) -> None:
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise AssertionError(f"{path} missing expected text: {missing}")

def forbid(path: str, *needles: str) -> None:
    text = read(path)
    found = [needle for needle in needles if needle in text]
    if found:
        raise AssertionError(f"{path} must not contain: {found}")

DOC = "docs/PHASE32_I_SOURCE_SYSTEM_ONLY_GOLDEN_PATH_RELEASE_GATE.md"
ACCEPTANCE = "scripts/acceptance/phase32i-source-system-only-golden-path.mjs"

require(
    DOC,
    "SourceSystem-only Golden Path Release Gate",
    "tenantId + sourceSystem only",
    "eventType/objectType/errorCode normalize to UNKNOWN",
    "TRIAGE Task with classificationStatus=UNCLASSIFIED",
    "Source Flow.default_pool_id",
    "TRIAGE_POOL",
    "Agent Pool member Agent",
    "Capability remains an Agent metadata/tag surface only",
    "SOURCE_FLOW_HAS_NO_DEFAULT_POOL",
    "POOL_HAS_NO_ACTIVE_MEMBER",
    "POOL_AGENT_RUNTIME_NOT_FOUND",
    "POOL_AGENT_OFFLINE",
    "POOL_AGENT_CAPACITY_FULL",
    "NO_ELIGIBLE_AGENT_IN_POOL",
    "make phase32-i-live",
)

require(
    ACCEPTANCE,
    "SOURCE_SYSTEM_ONLY_INTAKE_CONTRACT",
    "tenantId + sourceSystem only; no eventType/objectType/errorCode in payload",
    "NO_CAPABILITY_ROUTING_GATE_CONTRACT",
    "createDefaultCapabilities: false",
    "defaultCapabilities: []",
    "defaultTaskTypes: ['TRIAGE']",
    "poolType: 'TRIAGE'",
    "selectionStrategy: 'LOWEST_LOAD'",
    "flowType: 'SOURCE_FLOW'",
    "defaultPoolId: defaultPool",
    "defaultCapabilityRequirementMode: 'NONE'",
    "requiredSkills: []",
    "requiredCapabilities: []",
    "agents: []",
    "sourceSystemOnlyIntakePayload",
    "Intentionally does not include eventType, objectType, or errorCode",
    "assertUnknownTriagePoolAssignment",
    "assertNoCapabilityGate",
    "matchedRuleId",
    "SOURCE_DEFAULT",
    "SOURCE_FLOW_DEFAULT_POOL",
    "targetPoolId",
    "assignedPoolId",
    "POOL_HAS_NO_ACTIVE_MEMBER",
    "POOL_AGENT_OFFLINE",
    "POOL_AGENT_CAPACITY_FULL",
    "NO_ELIGIBLE_AGENT_IN_POOL",
)

# Verify the SourceSystem-only intake payload function does not set classification keys.
text = read(ACCEPTANCE)
match = re.search(r"function sourceSystemOnlyIntakePayload\(\) \{(?P<body>.*?)\n\}", text, re.S)
if not match:
    raise AssertionError("Acceptance gate missing sourceSystemOnlyIntakePayload function")
body = match.group("body")
for forbidden_key in ["eventType:", "objectType:", "errorCode:"]:
    if forbidden_key in body:
        raise AssertionError(f"sourceSystemOnlyIntakePayload must omit {forbidden_key}")

require(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java",
    "private String tenantId;",
    "private String sourceSystem;",
    "private String eventType;",
)
forbid(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java",
    "@NotBlank\n    private String eventType;",
)

require(
    "ai-event-gateway-core/event-processing/src/main/java/com/opensocket/aievent/core/normalize/EventNormalizer.java",
    'defaultString(request.getEventType(), UNKNOWN)',
    'defaultString(request.getObjectType(), UNKNOWN)',
    'defaultString(request.getErrorCode(), UNKNOWN)',
)

require(
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java",
    "TRIAGE",
    "UNCLASSIFIED",
    "SOURCE_FLOW_TRIAGE_PENDING",
)

require(
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
    "SOURCE_FLOW_DEFAULT_POOL",
    "targetPoolId",
    "assignedPoolId",
    "eligibleAgentCount",
    "phase32PoolFirst=true",
    "isPhase32PoolFirstTask",
    "routing_phase32_pool_first_bypassed_generic_authority",
    "SOURCE_FLOW_POOL_IS_AUTHORITATIVE",
    "immutableNullableMap(scoreBreakdown)",
    "immutableNullableMap(breakdown)",
    "Map.copyOf rejects null values",
)
forbid(
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
    "Map.copyOf(scoreBreakdown)",
    "Map.copyOf(breakdown)",
)

require(
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignmentService.java",
    "assignment.setAssignedPoolId",
    "assignment.setTargetPoolId",
)

require(
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
    "Target Pool",
    "Assigned Pool",
    "Pool Blocker",
)

require(
    "scripts/diagnostics/collect-dispatch-logs.sh",
    "docker compose -p",
    "COMPOSE_LOG_DIR",
    "compose.log",
    "for service in core netty",
    "${service}.log",
)

require(
    "Makefile",
    "verify-phase32i-source-system-only-golden-path",
    "phase32-i-acceptance-dry-run",
    "phase32-i-live",
    "phase32-release-gate",
    "node scripts/acceptance/phase32i-source-system-only-golden-path.mjs --dry-run --negative",
)

package_json = json.loads(read("ai-event-gateway-admin-ui/package.json"))
scripts = package_json.get("scripts", {})
for key in [
    "verify:phase32i-source-system-only-golden-path",
    "phase32i:acceptance:dry-run",
    "phase32i:acceptance:live",
]:
    if key not in scripts:
        raise AssertionError(f"ai-event-gateway-admin-ui/package.json missing script {key}")

require(
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java",
    "preserveNullableId(normalized.getDefaultPoolId())",
    "preserveNullableId(rule.getTargetPoolId())",
    "private static String preserveNullableId",
    "must not\n     * be normalized through normalizeCode()",
)
forbid(
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java",
    'normalizeNullable(normalized.getDefaultPoolId())',
    'normalizeNullable(rule.getTargetPoolId())',
)

require(
    "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcAgentPoolRoutingRepository.java",
    "normalizedPoolId",
    "normalizePoolId",
    "replace(replace(replace(p.pool_id, '-', '_'), '.', '_'), ' ', '_')",
)

require(
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchEligibilityController.java",
    '"/tasks/{taskId}/eligible-agents-v2"',
    "eligibleAgentsV2",
)

require(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/evidence/TaskRuntimeVerificationView.java",
    "new LinkedHashMap<>(diagnostics)",
    "Map.copyOf throws NullPointerException",
)

print("Phase 32-I SourceSystem-only golden path release gate contract verified.")
