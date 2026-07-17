#!/usr/bin/env python3
from pathlib import Path
import re

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


service = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowReadinessService.java"
controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java"
request = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowReadinessRequest.java"
response = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowReadinessResponse.java"
check = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowReadinessCheck.java"
candidate = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowCandidateAgentView.java"
ts_types = "ai-event-gateway-admin-ui/lib/types/core.ts"
ts_api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
ts_endpoints = "ai-event-gateway-admin-ui/lib/api/endpoints.ts"
ui = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx"

require(service, [
    "class DispatchFlowReadinessService",
    "public DispatchFlowReadinessResponse dryRun",
    "No ACTIVE Flow-owned Dispatch Rule matched this event",
    "FLOW_RULE_MATCH",
    "REQUESTED_SKILL",
    "FLOW_REQUIRED_SKILL",
    "FLOW_AGENT_ASSIGNMENT",
    "FLOW_AGENT_READINESS",
    "AGENT_SKILL_GRANT",
    "DISPATCHABLE_AGENT",
    "agent_capability_assignments",
    "flow_agent_assignments",
    "dispatch_policies",
    "P2_FLOW_RULE_DRY_RUN",
    "legacyReadinessUsed",
])

require(controller, [
    "DispatchFlowReadinessService",
    "@PostMapping(\"/dry-run\")",
    "@PostMapping(\"/{flowId}/dry-run\")",
    "@GetMapping(\"/{flowId}/readiness\")",
    "dispatchFlowReadinessService.dryRun",
])

for path in [request, response, check, candidate]:
    require(path, ["package com.opensocket.aievent.core.dispatch.flow;"])

require(response, [
    "private Boolean dispatchable",
    "private String firstBlockingCode",
    "private List<DispatchFlowReadinessCheck> checks",
    "private List<DispatchFlowCandidateAgentView> candidateAgents",
])

require(candidate, [
    "private Boolean requestedSkillGranted",
    "private Boolean dispatchable",
    "private List<String> blockingReasons",
])

require(ts_endpoints, [
    "dispatchFlowDryRun",
    "dispatchFlowScopedDryRun",
    "dispatchFlowReadiness",
])

require(ts_api, [
    "CoreDispatchFlowReadinessRequest",
    "CoreDispatchFlowReadinessResponse",
    "dryRunDispatchFlow(body",
    "dryRunDispatchFlowById(flowId",
])

require(ts_types, [
    "export interface CoreDispatchFlowReadinessRequest",
    "export interface CoreDispatchFlowReadinessResponse",
    "export interface CoreDispatchFlowCandidateAgentView",
    "candidateAgents?: CoreDispatchFlowCandidateAgentView[]",
])

require(ui, [
    "Flow Rule Dispatch Readiness",
    "buildFlowDryRunRequest",
    "coreAdminApi.dryRunDispatchFlow(",
    "coreAdminApi.dryRunDispatchFlowById",
    "Flow Rule dry-run blocked",
    "Requested Skill",
    "Selected Agent",
    "Skill Grant",
])

require_regex(ui, r"async function runReadiness\(\)[\s\S]*?dryRunDispatchFlow\(buildFlowDryRunRequest\(\)", "runReadiness must use Flow Rule dry-run instead of legacy contract readiness")
require_regex(ui, r"const readinessResponse = await coreAdminApi\.dryRunDispatchFlowById", "save flow must run scoped Flow Rule dry-run")

print("[verify-p2-flow-rule-dry-run-readiness] OK")
