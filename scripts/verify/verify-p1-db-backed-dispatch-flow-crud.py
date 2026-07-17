#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"Missing required file: {path}")
    return target.read_text(encoding="utf-8")


def require(path: str, needles: list[str]) -> None:
    content = read(path)
    missing = [needle for needle in needles if needle not in content]
    if missing:
        raise AssertionError(f"{path} is missing required markers: {missing}")


def require_regex(path: str, pattern: str, message: str) -> None:
    content = read(path)
    if not re.search(pattern, content, re.S):
        raise AssertionError(f"{path}: {message}")


service = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java"
java_rule_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowRuleView.java"
ts_api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
ts_types = "ai-event-gateway-admin-ui/lib/types/core.ts"
ui = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx"

require(service, [
    "class DispatchFlowManagementService",
    "insert into dispatch_flows",
    "insert into dispatch_policies",
    "insert into flow_required_capabilities",
    "insert into flow_agent_assignments",
    "on conflict (tenant_id, flow_id) do update",
    "on conflict (policy_id) do update",
    "p1DbBackedCrud",
])

require(controller, [
    "P1_DB_BACKED_CRUD",
    "DispatchFlowManagementService",
    "@PostMapping",
    "@PutMapping(\"/{flowId}\")",
    "@DeleteMapping(\"/{flowId}\")",
    "dispatchFlowManagementService.createOrUpdateFlow",
    # Stage 3 supersedes the original P1 child-by-child writes. The historical
    # CRUD contract remains DB-backed, but mutations now use one complete Flow
    # aggregate transaction.
    "Partial Dispatch Flow mutation is disabled",
    "HttpStatus.CONFLICT",
])

require_regex(controller, r"public List<DispatchFlowView> list[\s\S]*?return dispatchFlowManagementService\.listFlows", "list endpoint must use DB-backed service, not skeletonFlow")
require_regex(controller, r"public DispatchFlowView detail[\s\S]*?return dispatchFlowManagementService\.findFlow", "detail endpoint must use DB-backed service, not skeletonFlow")

require(java_rule_view, [
    "private String objectType;",
    "private String errorCode;",
    "getObjectType()",
    "setObjectType(String objectType)",
    "getErrorCode()",
    "setErrorCode(String errorCode)",
])

require(ts_types, [
    "objectType?: string;",
    "errorCode?: string;",
])

require(ts_api, [
    "createDispatchFlow(body: CoreDispatchFlowView",
    "updateDispatchFlow(flowId: string",
])

# Stage 3 intentionally removes the three partial mutation methods. Keeping
# them would allow half-written Flow aggregates and would violate the current
# authoritative transaction contract.
forbidden_partial_mutations = [
    "upsertDispatchFlowRule(flowId: string",
    "upsertDispatchFlowSkill(flowId: string",
    "upsertDispatchFlowAgent(flowId: string",
]
api_content = read(ts_api)
found_partial_mutations = [marker for marker in forbidden_partial_mutations if marker in api_content]
if found_partial_mutations:
    raise AssertionError(
        f"{ts_api} still exposes superseded partial Flow mutations: {found_partial_mutations}"
    )

require(ui, [
    "coreAdminApi.createDispatchFlow",
    "P1_DB_BACKED_CRUD",
    "legacyBootstrapRequired: false",
    "dispatch_policies.flow_id",
    "flow_required_capabilities",
    "flow_agent_assignments",
])

print("[verify-p1-db-backed-dispatch-flow-crud] OK")
