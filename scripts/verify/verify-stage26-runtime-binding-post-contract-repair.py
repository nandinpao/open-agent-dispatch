#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    raise SystemExit(f"[FAIL] {message}")


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        fail(f"{label} missing required fragment: {needle}")


def main() -> int:
    controller_path = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java"
    service_path = "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java"
    handler_path = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java"
    code_path = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/api/StandardApiErrorCode.java"
    bootstrap_path = "ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js"
    doc_path = "docs/PHASE26_RUNTIME_BINDING_POST_CONTRACT_REPAIR.md"
    makefile_path = "Makefile"

    controller = read(controller_path)
    service = read(service_path)
    handler = read(handler_path)
    code = read(code_path)
    bootstrap = read(bootstrap_path)
    doc = read(doc_path)
    makefile = read(makefile_path)

    require(bootstrap, "request('POST', `/admin/agents/${encodeURIComponent(agent)}/runtime-bindings`", bootstrap_path)
    require(controller, '@PostMapping("/agents/{agentId}/runtime-bindings")', controller_path)
    require(controller, "public AgentRuntimeBinding createRuntimeBinding", controller_path)
    require(controller, "service.upsertRuntimeBinding(agentId", controller_path)
    require(controller, '@PutMapping("/agents/{agentId}/runtime-bindings/{bindingId}")', controller_path)
    require(controller, '@PostMapping("/agents/{agentId}/runtime-bindings/{bindingId}/{targetStatus}")', controller_path)

    require(service, 'case "ACTIVATE", "ENABLED", "ENABLE", "RESUME", "RESUMED" -> "ACTIVE";', service_path)
    require(service, 'case "DEACTIVATE", "DISABLE", "DISABLED", "SUSPEND", "SUSPENDED", "PAUSE", "PAUSED" -> "SUSPENDED";', service_path)
    require(service, 'case "REVOKE", "REVOKED" -> "REVOKED";', service_path)

    require(handler, "HttpRequestMethodNotSupportedException", handler_path)
    require(handler, 'api_method_not_allowed', handler_path)
    require(handler, "StandardApiErrorCode.METHOD_NOT_ALLOWED", handler_path)
    require(handler, 'case "METHOD_NOT_ALLOWED" -> HttpStatus.METHOD_NOT_ALLOWED;', handler_path)
    require(code, 'METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "HTTP method is not allowed for this endpoint.")', code_path)

    for fragment in [
        "POST /admin/agents/{agentId}/runtime-bindings",
        "METHOD_NOT_ALLOWED",
        "bootstrap",
        "activate",
    ]:
        require(doc, fragment, doc_path)

    for fragment in [
        "verify-stage26-runtime-binding-post-contract-repair",
        "phase26-runtime-binding-post-contract-repair",
        "verify-stage26-runtime-binding-post-contract-repair.py",
    ]:
        require(makefile, fragment, makefile_path)

    print("Stage 26 runtime binding POST contract repair verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
