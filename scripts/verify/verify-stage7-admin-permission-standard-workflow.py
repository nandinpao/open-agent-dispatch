#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
failures = []

def fail(message: str):
    failures.append(message)

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        fail(f"missing required file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")

security = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java")
required_paths = [
    "/admin/source-systems/**",
    "/admin/agent-enrollments/**",
    "/admin/agents/**",
    "/admin/dispatch-flows/**",
    "/admin/tasks/**",
    "/admin/dispatch-requests/**",
]
for token in [
    "STANDARD_ADMIN_WORKFLOW_PATHS",
    "STANDARD_ADMIN_READ_ROLES",
    "STANDARD_ADMIN_MUTATION_ROLES",
]:
    if token not in security:
        fail(f"security config missing {token}")
for path in required_paths:
    if path not in security:
        fail(f"standard admin path is not explicit: {path}")
for role in ["VIEWER", "OPERATOR", "ADMIN"]:
    if f'"{role}"' not in security:
        fail(f"standard role missing from security config: {role}")
if "Admin permission is not allowed" in security:
    fail("generic access denied message must not remain")

legacy_index = security.find("requestMatchers(HttpMethod.GET, LEGACY_SUPPORT_PATHS)")
standard_index = security.find("requestMatchers(HttpMethod.GET, STANDARD_ADMIN_WORKFLOW_PATHS)")
if legacy_index == -1:
    fail("legacy support matcher is missing")
if standard_index == -1:
    fail("standard admin workflow matcher is missing")
if legacy_index != -1 and standard_index != -1 and legacy_index > standard_index:
    fail("legacy support matcher must be evaluated before standard admin workflow paths")

if ".requestMatchers(STANDARD_ADMIN_WORKFLOW_PATHS)\n                        .hasAnyRole(STANDARD_ADMIN_MUTATION_ROLES)" not in security:
    fail("standard workflow mutations must use STANDARD_ADMIN_MUTATION_ROLES")
if ".requestMatchers(HttpMethod.GET, STANDARD_ADMIN_WORKFLOW_PATHS)\n                        .hasAnyRole(STANDARD_ADMIN_READ_ROLES)" not in security:
    fail("standard workflow reads must use STANDARD_ADMIN_READ_ROLES")

# Standard app route directories must not reintroduce legacy/support/migration workflows.
legacy_route_parts = [
    "assignment-profiles",
    "supply-profiles",
    "dispatch-governance",
    "dispatch-readiness",
    "dispatch-simulator",
    "dispatch-recipes",
    "dispatch-task-definitions",
    "migration-readiness",
    "release-cutover",
]
app_dir = ROOT / "ai-event-gateway-admin-ui/app"
if app_dir.exists():
    for path in app_dir.rglob("page.tsx"):
        rel = path.relative_to(app_dir).as_posix()
        if any(part in rel for part in legacy_route_parts):
            fail(f"legacy/support/migration route still exposed in standard app: {rel}")
else:
    fail("missing Admin UI app directory")

# Navigation must not send users into legacy/support routes.
nav = read("ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts")
for part in legacy_route_parts:
    if f"/{part}" in nav or f"'{part}'" in nav or f'"{part}"' in nav:
        fail(f"navigation still references legacy/support route: {part}")

# Standard source/flow/task/agent pages should be present.
standard_pages = [
    "ai-event-gateway-admin-ui/app/source-systems/page.tsx",
    "ai-event-gateway-admin-ui/app/agents/page.tsx",
    "ai-event-gateway-admin-ui/app/dispatch-flows/page.tsx",
    "ai-event-gateway-admin-ui/app/tasks/page.tsx",
]
for rel in standard_pages:
    if not (ROOT / rel).exists():
        fail(f"standard Admin page missing: {rel}")

if failures:
    print("Stage 7 admin permission standard workflow verification failed:", file=sys.stderr)
    for item in failures:
        print(f" - {item}", file=sys.stderr)
    sys.exit(1)

print("Stage 7 admin permission standard workflow contract verified.")
