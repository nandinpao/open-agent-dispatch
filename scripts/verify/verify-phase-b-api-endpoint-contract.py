#!/usr/bin/env python3
"""Verify Admin UI endpoint constants against executable Core/Netty controller mappings.

This gate intentionally validates source code, not Markdown. The goal is to
prevent UI drift such as pages calling Netty task endpoints that do not exist.
Core remains the authoritative task plane; Netty remains runtime diagnostics.
"""
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
ENDPOINTS_TS = ROOT / "ai-event-gateway-admin-ui/lib/api/endpoints.ts"
ADMIN_API_TS = ROOT / "ai-event-gateway-admin-ui/lib/api/adminApi.ts"
VERIFY_RELEASE = ROOT / "scripts/verify/verify-release.py"

FORBIDDEN_FRONTEND_ENDPOINT_SNIPPETS = [
    "/api/cluster/tasks",
    "/api/cluster/tasks/by-node",
    "/api/admin/tasks",
    "/api/admin/runtime/sessions",
    "/api/admin/cluster/nodes/${encodeURIComponent(nodeId)}/tasks",
    "/api/admin/agents/${encodeURIComponent(agentId)}/tasks",
    "/api/admin/agents/${encodeURIComponent(agentId)}/errors",
    "/api/admin/events/${encodeURIComponent(eventId)}/trace",
]

REQUIRED_ADMIN_API_SNIPPETS = [
    "coreAdminApi.getTasksRuntimeView()",
    "coreTaskRuntimeToGatewayTask",
    "Tasks are loaded from Core /admin/tasks/runtime-view",
    "Netty task endpoints are intentionally not used because Netty is transport diagnostics, not task truth.",
]

REQUIRED_BACKEND_ROUTES = [
    "/admin/tasks/runtime-view",
    "/admin/tasks/{id}",
    "/admin/tasks/{id}/retry",
    "/admin/tasks/{id}/callback-inbox",
    "/admin/callbacks/inbox/recent",
    "/api/admin/runtime/agents",
    "/api/websocket/sessions",
    "/api/cluster/agents",
    "/api/admin/cluster/nodes/{id}/agents",
]

AGENT_SKILL_VERSION_ACTIONS = ["submit", "approve", "reject", "publish", "rollback"]


@dataclass(frozen=True)
class EndpointConstant:
    group: str
    name: str
    path: str


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def read_required(path: Path) -> str:
    if not path.is_file():
        fail(f"Missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def string_literals(raw: str) -> list[str]:
    # Supports Java annotations such as {"", "/summary", "/snapshot"} without
    # accidentally treating the comma between two quotes as a path.
    return [match.group(1) for match in re.finditer(r'"([^"]*)"', raw)]


def normalize_path(path: str) -> str:
    path = path.strip()
    if not path:
        return "/"
    if path.startswith("http://") or path.startswith("https://"):
        path = urlsplit(path).path
    else:
        path = path.split("?", 1)[0]
    path = re.sub(r"\$\{[^}]+\}", "{id}", path)
    path = re.sub(r"\{[^}/]+\}", "{id}", path)
    path = re.sub(r"//+", "/", path)
    if not path.startswith("/"):
        path = f"/{path}"
    if len(path) > 1 and path.endswith("/"):
        path = path.rstrip("/")
    return path


def join_mapping(base: str, child: str) -> str:
    base = base or ""
    child = child or ""
    if not base and not child:
        return "/"
    return normalize_path(f"{base.rstrip('/')}/{child.lstrip('/')}")


def extract_java_routes() -> set[str]:
    routes: set[str] = set()
    java_files = list((ROOT / "ai-event-gateway-core").rglob("*.java")) + list((ROOT / "ai-event-gateway-netty").rglob("*.java"))
    for path in java_files:
        text = path.read_text(encoding="utf-8", errors="ignore")
        class_match = re.search(r"@RequestMapping\((.*?)\)\s*(?:public\s+)?(?:abstract\s+)?class\s+\w+", text, re.DOTALL)
        base_paths = [""]
        if class_match:
            literals = string_literals(class_match.group(1))
            base_paths = literals or [""]

        for match in re.finditer(r"@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\((.*?)\))?", text, re.DOTALL):
            if match.group(1) == "Request":
                continue
            raw = match.group(2) or ""
            literals = string_literals(raw)
            child_paths = literals if literals else [""]
            for base in base_paths:
                for child in child_paths:
                    routes.add(join_mapping(base, child))
    return routes


def endpoint_body(source: str, object_name: str) -> str:
    marker = f"export const {object_name} = {{"
    if marker not in source:
        fail(f"Missing endpoint object: {object_name}")
    return source.split(marker, 1)[1].split("} as const;", 1)[0]


def extract_endpoint_constants() -> list[EndpointConstant]:
    source = read_required(ENDPOINTS_TS)
    constants: list[EndpointConstant] = []
    for group in ["coreAdminEndpoints", "nettyRuntimeEndpoints"]:
        for line in endpoint_body(source, group).splitlines():
            stripped = line.strip().rstrip(",")
            if ":" not in stripped:
                continue
            name, rhs = [part.strip() for part in stripped.split(":", 1)]
            path: str | None = None
            literal = re.search(r"^'([^']+)'$", rhs)
            if literal:
                path = literal.group(1)
            elif "=>" in rhs and "`" in rhs:
                path = rhs.split("`", 1)[1].rsplit("`", 1)[0]
            if path is None:
                continue
            constants.append(EndpointConstant(group, name, normalize_path(path)))
    return constants


def backend_has_route(backend_routes: set[str], endpoint: EndpointConstant) -> bool:
    if endpoint.group == "coreAdminEndpoints" and endpoint.name == "agentSkillVersionAction":
        return all(normalize_path(f"/admin/agent-skills/{{id}}/versions/{{id}}/{action}") in backend_routes for action in AGENT_SKILL_VERSION_ACTIONS)
    return endpoint.path in backend_routes


def main() -> int:
    endpoints_source = read_required(ENDPOINTS_TS)
    admin_api_source = read_required(ADMIN_API_TS)
    verify_release_source = read_required(VERIFY_RELEASE)

    for snippet in FORBIDDEN_FRONTEND_ENDPOINT_SNIPPETS:
        if snippet in endpoints_source or snippet in admin_api_source:
            fail(f"Forbidden unsupported frontend endpoint remains in source: {snippet}")

    for snippet in REQUIRED_ADMIN_API_SNIPPETS:
        if snippet not in admin_api_source:
            fail(f"Admin UI task fallback does not contain required Core-authoritative snippet: {snippet}")

    backend_routes = extract_java_routes()
    for route in REQUIRED_BACKEND_ROUTES:
        normalized = normalize_path(route)
        if normalized not in backend_routes:
            fail(f"Required backend route not found in executable controllers: {route}")

    constants = extract_endpoint_constants()
    missing = [endpoint for endpoint in constants if not backend_has_route(backend_routes, endpoint)]
    if missing:
        details = "\n".join(f"  - {endpoint.group}.{endpoint.name}: {endpoint.path}" for endpoint in missing)
        fail(f"Admin UI endpoint constants without executable backend mapping:\n{details}")

    if "frontend/backend endpoint contract" not in verify_release_source or "verify-phase-b-api-endpoint-contract.py" not in verify_release_source:
        fail("verify-release.py must include the Phase B frontend/backend endpoint contract gate")

    print(f"Phase B endpoint contract verification passed. endpointConstants={len(constants)} backendRoutes={len(backend_routes)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
