#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    service = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java")
    controller = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java")
    candidate_repo = read("ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcGenericCandidateAgentRepository.java")
    api = read("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts")
    endpoints = read("ai-event-gateway-admin-ui/lib/api/endpoints.ts")
    builder = read("ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx")
    detail_hook = read("ai-event-gateway-admin-ui/hooks/useAgentDetail.ts")
    option_type = read("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowAgentOptionView.java")

    require("/agent-options" in controller, "Dispatch Flow controller must expose tenant-scoped agent-options API.")
    require("/by-agent/{agentId}" in controller, "Dispatch Flow controller must expose by-agent API for Agent Detail.")
    require("agentOptions(String tenantId)" in service, "Dispatch Flow service must own Agent selector options.")
    require("listFlowsForAgent(String tenantId, String agentId)" in service, "Dispatch Flow service must own by-Agent Flow lookup.")
    require("flow_agent_assignments" in service, "Dispatch Flow service must read/write flow_agent_assignments as authoritative Agent relation.")
    require("dispatch_policies" in service and "flow_required_capabilities" in service, "Dispatch Flow aggregate must persist rules and required capabilities in the same service.")
    require("findExplicitFlowAgentIds" in candidate_repo and "flow_agent_assignments" in candidate_repo,
            "Runtime candidate repository must use flow_agent_assignments.")
    forbidden_candidate_tokens = ["agent_source_assignments", "source_defaults", "operation_profiles"]
    for token in forbidden_candidate_tokens:
        require(token not in candidate_repo.lower(), f"Runtime candidate repository must not read {token}.")

    require("dispatchFlowAgentOptions" in endpoints and "dispatchFlowsByAgent" in endpoints,
            "Admin API endpoints must include Flow agent options and by-Agent lookup.")
    require("getDispatchFlowAgentOptions" in api and "getDispatchFlowsForAgent" in api,
            "Core Admin API client must use dedicated Dispatch Flow source-of-truth endpoints.")
    require("getDispatchFlowAgentOptions(scopedTenantId)" in builder,
            "Dispatch Flow editor must load Agent options from /admin/dispatch-flows/agent-options.")
    require("coreAdminApi.getAgents()" not in builder,
            "Dispatch Flow editor must not use generic getAgents() and client-side tenant filtering.")
    require("agent.selectable" in builder and "disabledReason" in builder,
            "Dispatch Flow editor must render backend selectability and disabled reason.")
    require("getDispatchFlowsForAgent(scopedTenantId, agentId)" in detail_hook,
            "Agent Detail must use the same Dispatch Flow aggregate source rather than client-side filtering arbitrary data.")
    require("filter((flow) => (flow.agents" not in detail_hook,
            "Agent Detail must not reconstruct Flow relation by client-side filtering.")
    require("selectable" in option_type and "disabledReason" in option_type,
            "Agent option view must explicitly expose selectability and disabled reason.")
    require("selectedAgentId" in read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java"),
            "Task evidence facade must expose selectedAgentId evidence for Flow direct dispatch.")

    print("Stage 4 Dispatch Flow CRUD/runtime source-of-truth contract verified.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
