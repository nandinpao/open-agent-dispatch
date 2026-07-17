#!/usr/bin/env python3
"""Current OpenDispatch application verification gate.

This verifier is intentionally scoped to production/source code and runtime
configuration that affect the current app behavior. It avoids docs, reports,
.md5 inventories, and historical characterization artifacts so `make verify`
remains a useful day-to-day gate.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ERRORS: list[str] = []


def read(path: str) -> str:
    file = ROOT / path
    if not file.exists():
        raise AssertionError(f"missing required file: {path}")
    return file.read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> None:
    text = read(path)
    missing = [token for token in tokens if token not in text]
    if missing:
        raise AssertionError(f"{path} missing expected tokens: {missing}")


def forbid(path: str, *tokens: str) -> None:
    text = read(path)
    found = [token for token in tokens if token in text]
    if found:
        raise AssertionError(f"{path} contains forbidden tokens: {found}")


def check(label: str, fn) -> None:
    try:
        fn()
    except AssertionError as exc:
        ERRORS.append(f"{label}: {exc}")


def verify_intake_and_task_normalization() -> None:
    request = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java"
    require(request, "private String tenantId;", "private String sourceSystem;", "private String eventType;")
    forbid(request, "@NotBlank\n    private String eventType;")

    require(
        "ai-event-gateway-core/event-processing/src/main/java/com/opensocket/aievent/core/normalize/EventNormalizer.java",
        "defaultString(request.getEventType(), UNKNOWN)",
        "defaultString(request.getObjectType(), UNKNOWN)",
        "defaultString(request.getErrorCode(), UNKNOWN)",
    )
    require(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskType.java",
        "TRIAGE",
        "RESOLUTION",
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java",
        "TRIAGE",
        "UNCLASSIFIED",
        "SOURCE_FLOW_TRIAGE_PENDING",
    )


def verify_pool_persistence_and_flow_management() -> None:
    migration = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
    require(
        migration,
        "create table if not exists agent_pools",
        "create table if not exists agent_pool_members",
        "default_pool_id",
        "target_pool_id",
        "classification_status",
        "assigned_pool_id",
        "target_pool_id",
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java",
        "public List<AgentPoolView> listAgentPools",
        "public AgentPoolView createOrUpdateAgentPool",
        "validateAgentPoolReferences",
        "preserveNullableId(normalized.getDefaultPoolId())",
        "preserveNullableId(rule.getTargetPoolId())",
        "private static String preserveNullableId",
        "must not\n     * be normalized through normalizeCode()",
        "An ACTIVE Source Flow requires a default Agent Pool",
    )
    forbid(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java",
        "normalizeNullable(normalized.getDefaultPoolId())",
        "normalizeNullable(rule.getTargetPoolId())",
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java",
        '@GetMapping("/agent-pools")',
        '@PostMapping("/agent-pools")',
        '@PutMapping("/agent-pools/{poolId}")',
    )
    require(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/AgentPoolView.java",
        "private List<AgentPoolMemberView> members",
        "setMembers(List<AgentPoolMemberView> members)",
    )


def verify_pool_first_routing_and_assignment() -> None:
    require(
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java",
        "target_pool_id",
        "default_pool_id",
        "SOURCE_DEFAULT",
        "SOURCE_DEFAULT",
    )
    require(
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcAgentPoolRoutingRepository.java",
        "agent_pool_members",
        "normalizedPoolId",
        "normalizePoolId",
        "replace(replace(replace(p.pool_id, '-', '_'), '.', '_'), ' ', '_')",
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
        "isPhase32PoolFirstTask",
        "routing_phase32_pool_first_bypassed_generic_authority",
        "SOURCE_FLOW_POOL_IS_AUTHORITATIVE",
        "routing_pool_snapshot",
        "routing_pool_blocker",
        "poolMemberCount",
        "eligibleAgentCount",
        "targetPoolId",
        "assignedPoolId",
        "phase32PoolFirst=true",
        "immutableNullableMap(scoreBreakdown)",
        "immutableNullableMap(breakdown)",
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
        "POOL_AGENT_OFFLINE",
        "POOL_AGENT_CAPACITY_FULL",
        "NO_ELIGIBLE_AGENT_IN_POOL",
    )


def verify_triage_resolution_api() -> None:
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskClassificationController.java",
        '@PostMapping("/api/agent/tasks/{taskId}/classification-result")',
        '@PostMapping("/internal/tasks/{taskId}/classification-result")',
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskClassificationService.java",
        "ClassificationResultJson",
        "RESOLUTION",
        "parentTaskId",
        "assignIfPossible",
        "setRequiredCapabilities(List.of())",
    )


def verify_admin_ui_pool_first_surface() -> None:
    require(
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx",
        "Agent Pool 管理",
        "coreAdminApi.createAgentPool",
        "coreAdminApi.updateAgentPool",
    )
    require(
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx",
        "defaultPoolId",
        "targetPoolId",
        "SOURCE_SYSTEM_POOL",
        "Agent Pool / Work Queue",
        "Capability 是 Agent 能力標籤",
    )
    require(
        "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
        "TaskA2AEvidenceChainPanel",
        "Classification Result",
        "Resolution Child",
        "Target Pool",
        "Assigned Pool",
        "Pool Blocker",
        "Manage Pool Members",
        "Start Agent Runtime",
    )
    forbid(
        "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
        "MISSING_REQUIRED_CAPABILITY",
        "AGENT_REQUIRED_CAPABILITY_MISSING",
        "NO_FLOW_AGENT_ASSIGNMENT",
        "focus=required-capability",
    )
    require(
        "ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx",
        "Agent Pool / Work Queue",
        "Capability 僅是能力標籤參考",
    )
    require(
        "ai-event-gateway-admin-ui/hooks/useAgentDetail.ts",
        "agentPools: CoreAgentPoolView[]",
        "coreAdminApi.getAgentPools",
    )
    require(
        "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts",
        "getAgentPools",
        "createAgentPool",
        "updateAgentPool",
        "submitTaskClassificationResult",
    )
    require(
        "ai-event-gateway-admin-ui/lib/api/endpoints.ts",
        "agentPools: '/admin/dispatch-flows/agent-pools'",
        "agentPool: (poolId: string)",
        "taskClassificationResult",
    )
    require(
        "ai-event-gateway-admin-ui/lib/types/core.ts",
        "CoreAgentPoolView",
        "CoreTaskClassificationRequest",
        "targetPoolCode?: string",
        "poolMemberCount?: number",
        "eligibleAgentCount?: number",
    )


def verify_runtime_acceptance_and_diagnostics() -> None:
    acceptance = "scripts/acceptance/phase32i-source-system-only-golden-path.mjs"
    require(
        acceptance,
        "SOURCE_SYSTEM_ONLY_INTAKE_CONTRACT",
        "NO_CAPABILITY_ROUTING_GATE_CONTRACT",
        "sourceSystemOnlyIntakePayload",
        "assertUnknownTriagePoolAssignment",
        "assertNoCapabilityGate",
        "SOURCE_FLOW_DEFAULT_POOL",
        "POOL_HAS_NO_ACTIVE_MEMBER",
        "POOL_AGENT_OFFLINE",
        "POOL_AGENT_CAPACITY_FULL",
    )
    text = read(acceptance)
    match = re.search(r"function sourceSystemOnlyIntakePayload\(\) \{(?P<body>.*?)\n\}", text, re.S)
    if not match:
        raise AssertionError("missing sourceSystemOnlyIntakePayload function")
    body = match.group("body")
    for forbidden in ["eventType:", "objectType:", "errorCode:"]:
        if forbidden in body:
            raise AssertionError(f"sourceSystemOnlyIntakePayload must omit {forbidden}")

    require(
        "scripts/acceptance/api-envelope-runtime-acceptance.mjs",
        "parseExpectedStatuses",
        "P21_CORE_ERROR_HTTP_STATUSES",
        "expected HTTP status in",
    )
    require(
        "scripts/diagnostics/collect-dispatch-logs.sh",
        "docker compose -p",
        "COMPOSE_LOG_DIR",
        "compose.log",
        "for service in core netty",
        "${service}.log",
    )


def verify_makefile_policy() -> None:
    makefile = read("Makefile")
    if "verify-current:" not in makefile:
        raise AssertionError("Makefile missing verify-current target")
    target = re.search(r"^verify-current:\n(?P<body>(?:\t.*\n)+)", makefile, re.M)
    if not target:
        raise AssertionError("could not parse verify-current body")
    body = target.group("body")
    for token in [
        "verify-current-app-contract",
        "verify-local-compose-no-host-bind-mounts",
        "verify-p21-api-runtime-acceptance",
        "phase32-i-acceptance-dry-run",
    ]:
        if token not in body:
            raise AssertionError(f"verify-current does not include {token}")
    for forbidden in ["phase32-release-gate", "verify-legacy-release", "verify-release.py"]:
        if forbidden in body:
            raise AssertionError(f"verify-current must not call {forbidden}")
    help_block = makefile.split("check-project-layout:", 1)[0]
    if help_block.count("make verify-") > 8:
        raise AssertionError("help output still enumerates too many verify-* targets")


for label, fn in [
    ("intake/task normalization", verify_intake_and_task_normalization),
    ("pool persistence/flow management", verify_pool_persistence_and_flow_management),
    ("pool-first routing/assignment", verify_pool_first_routing_and_assignment),
    ("triage/resolution API", verify_triage_resolution_api),
    ("admin UI pool-first surface", verify_admin_ui_pool_first_surface),
    ("runtime acceptance/diagnostics", verify_runtime_acceptance_and_diagnostics),
    ("Makefile verification policy", verify_makefile_policy),
]:
    check(label, fn)

if ERRORS:
    print("Current app verification failed:", file=sys.stderr)
    for error in ERRORS:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("Current app verification contract passed without docs/md5 checks.")
