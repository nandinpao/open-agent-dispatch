#!/usr/bin/env python3
"""P4-B Admin UI Core session authentication and workspace authority guard."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
P4C_MARKER = ROOT / "docs/P4_C_AUTHENTICATION_FINAL_CONVERGENCE/architecture.md"
if P4C_MARKER.is_file():
    print("P4-C final authentication convergence supersedes this transitional verifier.")
    raise SystemExit(0)
UI = ROOT / "ai-event-gateway-admin-ui"
failures: list[str] = []

def read(path: Path) -> str:
    if not path.is_file():
        failures.append(f"missing file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def require(path: Path, text: str, message: str) -> None:
    if text not in read(path):
        failures.append(f"{path.relative_to(ROOT)}: {message}")

def forbid(path: Path, pattern: str, message: str) -> None:
    if re.search(pattern, read(path), flags=re.MULTILINE | re.DOTALL):
        failures.append(f"{path.relative_to(ROOT)}: {message}")

for path in [UI / "lib/auth/session.ts", UI / "lib/auth/ws.ts", UI / "lib/api/authApi.ts", UI / "components/auth/AuthProvider.tsx"]:
    forbid(path, r"localStorage|sessionStorage|getAccessToken|getRefreshToken|AUTH_STORAGE_KEYS", "browser authentication storage is forbidden")

require(UI / "lib/api/authApi.ts", "credentials: 'include'", "cookie credentials must be included")
require(UI / "lib/server/authProxy.ts", "setCookieHeaders", "Set-Cookie forwarding must be preserved")
require(UI / "lib/server/authProxy.ts", "serverAdminAuthMode() === 'netty-token'", "rollback switch is required")
require(UI / "components/layout/Topbar.tsx", "WorkspaceTenantSelector", "global workspace selector is required")
require(UI / "lib/auth/ws.ts", "HttpOnly cookie", "WebSocket must use HttpOnly cookie bridge")
require(ROOT / "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/websocket/WebSocketServerHandler.java", "OPENDISPATCH_ADMIN_WS_TOKEN", "Netty WebSocket handshake must accept the HttpOnly bridge cookie")

for path in [
    UI / "components/agents/AgentEnrollmentTable.tsx",
    UI / "components/agents/AgentOnboardingPanel.tsx",
    UI / "components/agents/AgentProfileEditDialog.tsx",
    UI / "components/agents/AgentEnrollmentReviewDialog.tsx",
    UI / "components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx",
    UI / "components/dispatch-governance/DispatchGovernanceConsole.tsx",
    UI / "components/dispatch-readiness/DispatchReadinessWizard.tsx",
    UI / "components/dispatch-recipes/DispatchRecipeWizard.tsx",
    UI / "components/dispatch-simulator/DispatchSimulatorConsole.tsx",
]:
    forbid(path, r"onChange=\{[^\n]*(?:setTenantId|setField\([\"']tenantId)", "tenant must not be freely editable")

require(ROOT / "deploy/env/.env.release.example", "NEXT_PUBLIC_ADMIN_AUTH_MODE=core-session", "release browser auth must use Core Session")
require(ROOT / "deploy/env/.env.release.example", "ADMIN_UI_AUTH_MODE=core-session", "release server auth must use Core Session")
require(ROOT / "deploy/env/.env.release.example", "ADMIN_UI_COOKIE_SECURE=true", "release proxy cookies must be Secure")
require(ROOT / "deploy/env/.env.release.example", "NEXT_PUBLIC_WS_AUTH_MODE=cookie", "release WebSocket auth must use cookie handshake")
forbid(ROOT / "deploy/env/.env.release.example", r"NEXT_PUBLIC_AUTH_STORAGE=", "obsolete browser auth storage variable is forbidden")

for path in [ROOT / "deploy/docker-compose.local.yml", ROOT / "deploy/docker-compose.ci.yml", ROOT / "deploy/docker-compose.release.yml"]:
    require(path, "NEXT_PUBLIC_ADMIN_AUTH_MODE", "Admin UI auth mode must be configured")
    require(path, "NEXT_PUBLIC_WS_AUTH_MODE", "WebSocket auth mode must be configured")
    forbid(path, r"NEXT_PUBLIC_AUTH_STORAGE", "obsolete browser auth storage setting is forbidden")

require(UI / "scripts/verify-p4b-core-auth.mjs", "browser token storage is forbidden", "frontend release guard is required")
require(UI / "scripts/p4b-auth-browser-e2e.mjs", "/api/auth/logout", "browser authentication E2E is required")
require(UI / "tests/p4b-auth-cookie-session.test.ts", "without an Authorization token", "cookie session unit test is required")

if failures:
    print("P4-B Admin UI Core authentication verification failed:", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

print("P4-B Admin UI Core authentication verification passed.")
