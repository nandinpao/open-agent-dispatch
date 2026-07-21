#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        print(f"Missing token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    service = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
    controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java"
    handler = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java"
    error_code = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/api/StandardApiErrorCode.java"
    pool_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/AgentPoolView.java"
    member_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/AgentPoolMemberView.java"
    flow_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowView.java"
    types = "ai-event-gateway-admin-ui/lib/types/core.ts"
    api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
    pool_ui = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx"
    flow_ui = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx"

    require(error_code, "RESOURCE_VERSION_CONFLICT")
    require(handler, '"RESOURCE_VERSION_CONFLICT"')

    for model in (pool_view, member_view, flow_view):
        require(model, "private Integer version;")
        require(model, "private String updatedBy;")
        require(model, "getVersion()")
        require(model, "getUpdatedBy()")

    require(controller, '@RequestHeader(value = "If-Match", required = false) String ifMatch')
    require(controller, "expectedVersion(ifMatch, request.getVersion()")
    require(controller, "parseIfMatchVersion")
    require(controller, "If-Match must contain the numeric configuration version")

    require(service, "createOrUpdateAgentPool(AgentPoolView request, Integer expectedVersion)")
    require(service, "createOrUpdateFlow(DispatchFlowView request, Integer expectedVersion)")
    require(service, "where agent_pools.version = :expectedVersion")
    require(service, "where dispatch_flows.version = :expectedVersion")
    require(service, "assertExpectedVersion")
    require(service, "throwVersionConflict")
    require(service, "p.version")
    require(service, "p.updated_by")
    require(service, "f.version")
    require(service, "f.updated_by")
    require(service, "m.version")
    require(service, "m.updated_by")

    require(types, "version?: number;")
    require(types, "updatedBy?: string;")
    require(api, "function optimisticLockHeaders")
    require(api, "'If-Match': String(Math.trunc(version))")
    require(api, "headers: optimisticLockHeaders(body.version)")

    require(pool_ui, "RESOURCE_VERSION_CONFLICT")
    require(pool_ui, "此 Agent Pool 已被其他管理員更新")
    require(pool_ui, "version: editor.version")
    require(pool_ui, "版本 {editor.version}")

    require(flow_ui, "RESOURCE_VERSION_CONFLICT")
    require(flow_ui, "此派工流程已被其他管理員更新")
    require(flow_ui, "version: editor.version")
    require(flow_ui, "版本 {editor.version}")

    require("docs/current/development/phase2-6-api-ui-optimistic-lock-conflict-handling.md", "RESOURCE_VERSION_CONFLICT")
    require("docs/current/phase2-6-change-log.md", "Admin UI")
    require("docs/current/phase2-6-modified-files.md", "DispatchFlowManagementService.java")

    print("Phase 2-6 API/UI optimistic-lock conflict handling contract verified.")


if __name__ == "__main__":
    main()
