#!/usr/bin/env python3
"""P4-C final authentication boundary, session governance, and browser E2E guard."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "ai-event-gateway-admin-ui"
failures: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        failures.append(f"missing file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def require(path: Path, token: str, message: str) -> None:
    if token not in read(path):
        failures.append(f"{path.relative_to(ROOT)}: {message}")


def forbid(path: Path, pattern: str, message: str) -> None:
    if path.exists() and re.search(pattern, read(path), re.MULTILINE | re.DOTALL):
        failures.append(f"{path.relative_to(ROOT)}: {message}")


def require_missing(path: Path, message: str) -> None:
    if path.exists():
        failures.append(f"{path.relative_to(ROOT)}: {message}")


require(ROOT / "docs/P4_C_AUTHENTICATION_FINAL_CONVERGENCE/architecture.md", "Core the only authority", "architecture boundary marker is required")

for relative in [
    "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminAuthService.java",
    "ai-event-gateway-netty/boot-starter/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminTokenAuthFilter.java",
    "ai-event-gateway-netty/security/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminAccessRole.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/dto/AdminLoginRequest.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/dto/AdminAuthResponse.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/dto/AdminMeResponse.java",
    "ai-event-gateway-admin-ui/lib/auth/ws.ts",
]:
    require_missing(ROOT / relative, "legacy Human Admin authentication artifact must be removed")

machine_filter = ROOT / "ai-event-gateway-netty/boot-starter/src/main/java/com/opensocket/aievent/gateway/netty/admin/MachineAdminTokenAuthFilter.java"
require(machine_filter, "MACHINE_AUTHENTICATED_ATTRIBUTE", "machine-only authentication filter is required")
require(machine_filter, "X-Machine-Token", "explicit machine credential header is required")
forbid(machine_filter, r"Cookie|queryString|viewerToken|operatorToken|username|password", "machine filter must not accept browser or human credentials")

admin_properties = ROOT / "ai-event-gateway-netty/boot-starter/src/main/java/com/opensocket/aievent/gateway/netty/config/AdminProperties.java"
for token in ["machineAuthEnabled", "machineToken", "internalToken", "machineWebSocketHandshakeAuthEnabled"]:
    require(admin_properties, token, f"Netty machine property {token} is required")
forbid(admin_properties, r"\b(apiToken|viewerToken|operatorToken|password|username|refreshToken)\b", "Netty Human Admin properties are forbidden")

cluster_state_pull_client = ROOT / "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/cluster/sync/ClusterStatePullClient.java"
require(cluster_state_pull_client, "adminProperties.machineAuthEnabled()", "cluster state pull must use the machine authentication property")
forbid(cluster_state_pull_client, r"adminProperties\.authEnabled\(\)", "removed Human Admin authEnabled accessor must not be referenced")

controller = ROOT / "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminUiController.java"
forbid(controller, r'@(?:Get|Post)Mapping\(\"/(?:auth/)?(?:login|refresh|logout|me)\"\)', "Netty Human Admin endpoint is forbidden")

core_controller = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/AdminAuthenticationController.java"
for token in [
    '@GetMapping("/sessions")',
    '@PostMapping("/sessions/{sessionReference}/revoke")',
    '@GetMapping("/security-audit")',
]:
    require(core_controller, token, f"Core authentication endpoint is missing: {token}")

session_service = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/service/AdminSessionManagementService.java"
require(session_service, "reference(session.getId())", "browser-safe session references are required")
require(session_service, "MessageDigest.isEqual", "session reference comparison must be constant-time")
session_application = ROOT / "ai-event-gateway-core/control-plane-app/src/main/resources/application.yml"
require(session_application, "repository-type: indexed", "Admin session inventory requires the Boot 4 indexed Redis session repository")
require(session_application, "data:\n      redis:", "Spring Boot 4 Redis Session properties must use spring.session.data.redis")
if "session:\n    timeout: ${CORE_ADMIN_SESSION_TIMEOUT:30m}\n    redis:" in session_application.read_text(encoding="utf-8"):
    failures.append("legacy spring.session.redis configuration is ignored by Spring Boot 4")
require(ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/identity/service/AdminSessionRedisConfigurationTest.java", "spring.session.data.redis.repository-type", "Boot 4 indexed Redis session configuration regression test is required")
forbid(ROOT / "ai-event-gateway-core/identity-access/src/main/java/com/opensocket/aievent/core/identity/dto/AdminSessionDescriptor.java", r"String\s+sessionId", "raw session identifiers must not be exposed to browser JavaScript")

security = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java"
require(security, 'requestMatchers(HttpMethod.GET, "/api/auth/security-audit").hasRole("ADMIN")', "security audit must be ADMIN-only")
require(security, 'requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()', "Core login endpoint must remain available behind CSRF")
require(security, "static String[] distinctRoles(String... roles)", "Human Admin role groups must be normalized before Spring Security hasAnyRole")
forbid(security, r"\.hasAnyRole\((?!distinctRoles\()", "all Human Admin hasAnyRole calls must use duplicate-safe role normalization")
require(ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfigurationTest.java", "distinctRolesRemovesDuplicateHumanAndMachineRoleNames", "duplicate Human/machine role regression test is required")

realtime = UI / "app/api/realtime/events/route.ts"
require(realtime, "/api/auth/me", "realtime relay must validate the Core session")
require(realtime, "NETTY_MACHINE_ADMIN_TOKEN", "realtime relay must use server-only machine authentication")
require(realtime, "text/event-stream", "realtime relay must use Server-Sent Events")
require(UI / "components/providers/AdminRealtimeProvider.tsx", "new EventSource('/api/realtime/events'", "browser realtime client must use same-origin SSE")
require(UI / "components/settings/AuthenticationSecurityPanel.tsx", "Server-side sessions", "Settings must expose server-side session management")
require(UI / "components/settings/AuthenticationSecurityPanel.tsx", "Authentication security audit", "Settings must expose ADMIN authentication audit evidence")
require(UI / "components/settings/AuthenticationSecurityPanel.tsx", "Confirm revoke", "session revocation must use an explicit confirmation step")

for path in [UI / "lib/server/authProxy.ts", UI / "lib/server/backendProxy.ts", UI / "lib/auth/session.ts", UI / "lib/api/authApi.ts"]:
    forbid(path, r"netty-token|OPENDISPATCH_ADMIN_WS_TOKEN|OPENDISPATCH_LEGACY_ADMIN_TOKEN|accessToken|refreshToken|localStorage|sessionStorage", "legacy browser token or rollback path is forbidden")

obsolete_pattern = re.compile(r"(?m)^(?:\s*)(?:ADMIN_API_TOKEN|ADMIN_VIEWER_TOKEN|ADMIN_OPERATOR_TOKEN|ADMIN_PASSWORD|NEXT_PUBLIC_ADMIN_AUTH_MODE|ADMIN_UI_AUTH_MODE|NEXT_PUBLIC_WS_AUTH_MODE|NEXT_PUBLIC_WS_TOKEN_QUERY_NAME)(?:\s*:|=)")
for path in [
    ROOT / "deploy/docker-compose.local.yml",
    ROOT / "deploy/docker-compose.ci.yml",
    ROOT / "deploy/docker-compose.release.yml",
    ROOT / "deploy/env/.env.local.example",
    ROOT / "deploy/env/.env.local.ci",
    ROOT / "deploy/env/.env.release.example",
    UI / ".env.local.example",
    UI / ".env.production.example",
    ROOT / "ai-event-gateway-core/Jenkinsfile",
]:
    if path.is_file() and obsolete_pattern.search(read(path)):
        failures.append(f"{path.relative_to(ROOT)}: obsolete Human Admin/rollback environment variable is forbidden")

for path in [ROOT / "deploy/docker-compose.local.yml", ROOT / "deploy/docker-compose.ci.yml", ROOT / "deploy/docker-compose.release.yml"]:
    require(path, "NETTY_MACHINE_ADMIN_TOKEN", "Netty machine credential must be wired server-side")

local_compose = ROOT / "deploy/docker-compose.local.yml"
require(local_compose, "CORE_ADMIN_BOOTSTRAP_ENABLED: ${CORE_ADMIN_BOOTSTRAP_ENABLED:-true}", "normal local startup must enable the bootstrap administrator")
require(local_compose, "CORE_ADMIN_E2E_ADMIN_ENABLED: ${CORE_ADMIN_E2E_ADMIN_ENABLED:-false}", "normal local startup must not replace the bootstrap administrator with E2E accounts")
require(local_compose, "CORE_ADMIN_E2E_OPERATOR_ENABLED: ${CORE_ADMIN_E2E_OPERATOR_ENABLED:-false}", "normal local startup must keep the E2E operator disabled")
require(local_compose, "CORE_ADMIN_E2E_VIEWER_ENABLED: ${CORE_ADMIN_E2E_VIEWER_ENABLED:-false}", "normal local startup must keep the E2E viewer disabled")
require(local_compose, "CORE_ADMIN_BOOTSTRAP_PASSWORD: ${CORE_ADMIN_BOOTSTRAP_PASSWORD:-local-admin-change-me}", "local fallback password must match the documented environment template")
local_env = ROOT / "deploy/env/.env.local.example"
require(local_env, "NEXT_PUBLIC_AUTH_ENABLED=true", "local Admin UI authentication must be enabled")
require(local_env, "CORE_ADMIN_BOOTSTRAP_ENABLED=true", "local bootstrap administrator must be explicitly enabled")
for token in ["CORE_ADMIN_E2E_ADMIN_ENABLED=false", "CORE_ADMIN_E2E_OPERATOR_ENABLED=false", "CORE_ADMIN_E2E_VIEWER_ENABLED=false"]:
    require(local_env, token, "normal local startup must keep dedicated E2E identities disabled")
require(UI / "lib/server/authProxy.ts", "CORE_AUTH_UNAVAILABLE", "authentication proxy must return a diagnosable Core-unavailable response")
login_smoke = ROOT / "scripts/security/core-admin-login-smoke.py"
for token in ["/api/auth/csrf", "/api/auth/login", "/api/auth/me", "/api/auth/logout"]:
    require(login_smoke, token, f"local credential smoke must exercise {token}")
require(ROOT / "scripts/ci/local-smoke.sh", "core-admin-login-smoke.py", "cd-local smoke must verify the configured browser login credential")
require(local_env, "SMOKE_ADMIN_AUTH_USERNAME=admin", "local smoke username must match the bootstrap administrator")
require(local_env, "SMOKE_ADMIN_AUTH_PASSWORD=local-admin-change-me", "local smoke password must match the documented bootstrap password")
require(ROOT / "deploy/env/.env.local.ci", "SMOKE_ADMIN_AUTH_USERNAME=admin-e2e", "CI smoke must use the dedicated E2E ADMIN account")

identity_repository = ROOT / "ai-event-gateway-core/identity-access/src/main/java/com/opensocket/aievent/core/identity/ConfiguredAdminIdentityRepository.java"
require(identity_repository, "definitions.add(properties.getBootstrap())", "enabled bootstrap administrator must coexist with additional configured accounts")
require(identity_repository, ".forEach(definitions::add)", "enabled additional accounts must be appended instead of replacing bootstrap administrator")
require(identity_repository, "usesPlaintextPassword(account)", "local/CI plaintext credential selection must be explicit")
require(identity_repository, "return passwordEncoder.encode(account.getPlaintextPassword());", "explicit local/CI plaintext password must take precedence and be encoded once at startup")
require(identity_repository, "LOCAL_PLAINTEXT_ENCODED_AT_STARTUP", "credential source must be diagnosable without logging secrets")
forbid(identity_repository, r"!definition\.getPasswordHash\(\)\.isBlank\(\)\s*\?", "stale password hash must not silently override explicit local plaintext mode")
forbid(identity_repository, r"if \(definitions\.isEmpty\(\) && properties\.getBootstrap\(\)\.isEnabled\(\)\)", "additional accounts must never silently remove the bootstrap administrator")
require(ROOT / "scripts/security/p4c-auth-browser-e2e.sh", "CORE_ADMIN_SESSION_TIMEOUT", "P4-C Docker smoke orchestration must force a short session timeout")
require(ROOT / "scripts/security/p4c-auth-browser-e2e.sh", "restore_stack", "P4-C Docker smoke orchestration must restore the normal session timeout")
identity_repository_test = ROOT / "ai-event-gateway-core/identity-access/src/test/java/com/opensocket/aievent/core/identity/ConfiguredAdminIdentityRepositoryTest.java"
require(identity_repository_test, "loadsMultipleConfiguredAccountsWithIndependentRolesAndTenantScopes", "multi-role identity repository test is required")
require(identity_repository_test, "keepsBootstrapAdministratorWhenAdditionalAccountsAreEnabled", "bootstrap/additional-account coexistence regression test is required")
require(identity_repository_test, "explicitLocalPlaintextTakesPrecedenceOverStalePasswordHash", "local plaintext authority regression test is required")
require(ROOT / "ai-event-gateway-core/identity-access/src/test/java/com/opensocket/aievent/core/identity/ConfiguredAdminIdentityRepositoryTest.java", "rejectsDuplicateConfiguredUsernamesCaseInsensitively", "duplicate identity regression test is required")
require(ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/identity/service/AdminSessionManagementServiceTest.java", "doesNotContain(stored.getId())", "session identifier exposure regression test is required")
require(ROOT / "ai-event-gateway-netty/boot-starter/src/test/java/com/opensocket/aievent/gateway/netty/admin/MachineAdminTokenAuthFilterTest.java", "neverAcceptsBrowserCookieOrQueryToken", "machine-only authentication regression test is required")

e2e = UI / "scripts/p4c-auth-browser-e2e.mjs"
for token in ["missing CSRF", "VIEWER", "OPERATOR", "ADMIN", "cross-tenant", "session expiration", "force revoke", "/api/auth/logout"]:
    require(e2e, token, f"P4-C Browser E2E must cover {token}")

if failures:
    print("P4-C authentication final convergence verification failed:", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)
print("P4-C authentication final convergence verification passed.")
