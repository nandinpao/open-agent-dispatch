#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
P4C_MARKER = ROOT / "docs/P4_C_AUTHENTICATION_FINAL_CONVERGENCE/architecture.md"
if P4C_MARKER.is_file():
    print("P4-C final authentication convergence supersedes this transitional verifier.")
    raise SystemExit(0)


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: str) -> str:
    file = ROOT / path
    if not file.is_file():
        fail(f"Missing P4-A file: {path}")
    return file.read_text(encoding="utf-8")


def require(path: str, markers: list[str]) -> str:
    text = read(path)
    for marker in markers:
        if marker not in text:
            fail(f"{path} is missing required P4-A marker: {marker}")
    return text


def env_values(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in read(path).splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def main() -> int:
    parent = require("ai-event-gateway-core/pom.xml", ["<module>identity-access</module>", "<artifactId>identity-access</artifactId>"])
    app_pom = require("ai-event-gateway-core/control-plane-app/pom.xml", [
        "<artifactId>identity-access</artifactId>",
        "<artifactId>spring-boot-starter-session-data-redis</artifactId>",
        "<artifactId>spring-security-test</artifactId>",
    ])
    identity_pom = require("ai-event-gateway-core/identity-access/pom.xml", [
        "<artifactId>spring-security-core</artifactId>",
        "<artifactId>spring-security-crypto</artifactId>",
        "<artifactId>jakarta.validation-api</artifactId>",
    ])
    for pom in (parent, app_pom, identity_pom):
        try:
            ET.fromstring(pom)
        except ET.ParseError as exc:
            fail(f"P4-A Maven XML is invalid: {exc}")

    principal = require(
        "ai-event-gateway-core/identity-access/src/main/java/com/opensocket/aievent/core/identity/AdminPrincipal.java",
        [
            "implements UserDetails, CredentialsContainer, Serializable",
            "String userId",
            "Set<AdminRole> roles",
            "Set<String> permissions",
            "Set<String> allowedTenantIds",
            "String selectedTenantId",
            "withSelectedTenant(String tenantId)",
        ],
    )
    if "static final ThreadLocal" in principal:
        fail("Core human principal must remain serializable session data, not ThreadLocal state")

    require(
        "ai-event-gateway-core/identity-access/src/main/java/com/opensocket/aievent/core/identity/ConfiguredAdminIdentityRepository.java",
        [
            "if (!properties.isEnabled())",
            "no password hash is configured",
            "isAllowPlaintextPassword",
            "configuredAccount",
            "normalizedUsername",
            "isDelegatingPasswordHash",
        ],
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/AdminAuthenticationController.java",
        [
            '@PostMapping("/login")',
            '@PostMapping("/logout")',
            '@GetMapping("/me")',
            '@GetMapping("/permissions")',
            '@GetMapping("/tenants")',
            '@PostMapping("/select-tenant")',
            '@GetMapping("/csrf")',
        ],
    )
    service = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/service/AdminAuthenticationService.java",
        [
            "authenticationManager.authenticate(candidate)",
            "sessionAuthenticationStrategy.onAuthentication",
            "securityContextRepository.saveContext",
            "session.setMaxInactiveInterval",
            "logoutHandler.logout",
            "withSelectedTenant(tenantId)",
        ],
    )
    if "localStorage" in service or "sessionStorage" in service:
        fail("Core human authentication must use server-side session, not browser storage")

    security = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java",
        [
            "@Order(1)",
            "coreInternalMachineSecurityFilterChain",
            'securityMatcher("/internal/**", "/actuator/**")',
            "SessionCreationPolicy.STATELESS",
            "@Order(2)",
            "coreEventIntakeSecurityFilterChain",
            'securityMatcher("/api/events/**")',
            "@Order(3)",
            "coreHumanAdminSecurityFilterChain",
            'securityMatcher("/api/auth/**", "/admin/**")',
            "SessionCreationPolicy.IF_REQUIRED",
            "HttpSessionSecurityContextRepository",
            "CookieCsrfTokenRepository.withHttpOnlyFalse()",
            "ignoringRequestMatchers(validInternalToken)",
            ".requireExplicitSave(true)",
            "@Order(4)",
            "coreFallbackSecurityFilterChain",
        ],
    )
    if "loginRequest" in security:
        fail("Core Admin login must obtain a CSRF token from GET /api/auth/csrf before credential submission")
    if re.search(r"ignoringRequestMatchers\([^)]*(?:X-Cluster-Token|X-Internal-Token)", security):
        fail("CSRF bypass must require verified internal credentials, not header presence")
    if "csrf(AbstractHttpConfigurer::disable)" not in security:
        fail("Machine and event-intake chains must remain explicitly stateless and CSRF-free")

    token_filter = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalTokenAuthenticationFilter.java",
        ["existing instanceof AnonymousAuthenticationToken", "role == CoreInternalSecurityRole.RECOVERY_ADMIN"],
    )
    if "if (role == CoreInternalSecurityRole.RECOVERY_APPROVER)" in token_filter:
        fail("Recovery approver must not inherit recovery-admin authority")

    verifier = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalTokenVerifier.java",
        ["MessageDigest.isEqual", "isValidInternalTokenRequest", "verification.required() && verification.accepted()"],
    )
    classifier = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityRequestClassifier.java",
        ['path.startsWith("/api/auth/")', "CoreInternalSecurityRole.EVENT_INGESTION"],
    )
    if classifier.index('path.startsWith("/api/auth/")') > classifier.index('properties.isProtectApiMutations()'):
        fail("Core auth endpoints must be excluded before generic API mutation classification")

    request_filter = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/context/OpenDispatchRequestContextFilter.java",
        [
            "authentication.getPrincipal() instanceof AdminPrincipal principal",
            "principal.selectedTenantId()",
            "resolveTenantId(request)",
        ],
    )
    if "principal.selectedTenantId()" not in request_filter:
        fail("Human session tenant must be authoritative for request context")

    auth_advice = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/AdminIdentityExceptionHandler.java",
        ["@Order(Ordered.HIGHEST_PRECEDENCE)", "HttpStatus.UNAUTHORIZED", "HttpStatus.BAD_REQUEST", "HttpStatus.SERVICE_UNAVAILABLE"],
    )
    global_advice = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java",
        ["@Order(Ordered.HIGHEST_PRECEDENCE + 1)"],
    )
    if "ResponseEntity.ok" not in global_advice:
        fail("P4-A expects the historical API envelope handler to remain unchanged outside auth endpoints")

    application_yml = require("ai-event-gateway-core/control-plane-app/src/main/resources/application.yml", [
        "spring:\n  application:",
        "session:\n    timeout: ${CORE_ADMIN_SESSION_TIMEOUT:30m}",
        "namespace: ${CORE_ADMIN_SESSION_REDIS_NAMESPACE:opendispatch:core:admin:sessions}",
        "http-only: true",
        "enabled: ${CORE_ADMIN_AUTH_ENABLED:false}",
        "password-hash: ${CORE_ADMIN_BOOTSTRAP_PASSWORD_HASH:}",
        "allow-plaintext-password: ${CORE_ADMIN_BOOTSTRAP_ALLOW_PLAINTEXT_PASSWORD:false}",
        "event-intake-token:",
    ])
    if "CORE_ADMIN_BOOTSTRAP_PASSWORD:admin" in application_yml or "CORE_ADMIN_BOOTSTRAP_PASSWORD:password" in application_yml:
        fail("application.yml must not contain a default Core Admin plaintext password")
    if "CORE_ADMIN_BOOTSTRAP_ALLOWED_TENANTS:tenant-a" in application_yml or "CORE_ADMIN_BOOTSTRAP_DEFAULT_TENANT:tenant-a" in application_yml:
        fail("Core Admin application defaults must remain tenant-neutral")

    for path in [
        "deploy/env/.env.release.example",
        "deploy/env/.env.prod.security.example",
    ]:
        values = env_values(path)
        if values.get("CORE_ADMIN_AUTH_ENABLED") != "true":
            fail(f"{path} must explicitly enable Core human authentication")
        if values.get("CORE_ADMIN_SESSION_COOKIE_SECURE") != "true":
            fail(f"{path} must require Secure session cookies")
        if values.get("CORE_ADMIN_BOOTSTRAP_ALLOW_PLAINTEXT_PASSWORD") != "false":
            fail(f"{path} must reject plaintext bootstrap passwords")
        if not values.get("CORE_ADMIN_BOOTSTRAP_PASSWORD_HASH"):
            fail(f"{path} must require a delegating password hash placeholder")
        if values.get("CORE_ADMIN_BOOTSTRAP_PASSWORD"):
            fail(f"{path} must leave plaintext bootstrap password empty")
        allowed_tenants = values.get("CORE_ADMIN_BOOTSTRAP_ALLOWED_TENANTS", "")
        default_tenant = values.get("CORE_ADMIN_BOOTSTRAP_DEFAULT_TENANT", "")
        if not allowed_tenants or not default_tenant or "tenant-a" in {allowed_tenants, default_tenant}:
            fail(f"{path} must use explicit tenant placeholders instead of fixed product tenants")

    for path in [
        "deploy/docker-compose.local.yml",
        "deploy/docker-compose.ci.yml",
        "deploy/docker-compose.release.yml",
    ]:
        compose = require(path, [
            "CORE_ADMIN_AUTH_ENABLED:",
            "CORE_ADMIN_SESSION_REDIS_NAMESPACE:",
            "CORE_ADMIN_BOOTSTRAP_PASSWORD_HASH:",
            "CORE_EVENT_INTAKE_INTERNAL_TOKEN:",
        ])
        if path.endswith("docker-compose.release.yml") and "CORE_ADMIN_BOOTSTRAP_ALLOWED_TENANTS:-tenant-a" in compose:
            fail("Release compose must not default Core Admin access to a fixed tenant")

    # P4-A remains dual-track at the backend boundary. P4-B may route the browser through
    # the same-origin auth proxy while retaining an explicit server-side Netty rollback adapter.
    require(
        "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminAuthService.java",
        ["class AdminAuthService"],
    )
    require(
        "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminUiController.java",
        ['@PostMapping("/auth/login")'],
    )
    require(
        "ai-event-gateway-admin-ui/lib/server/authProxy.ts",
        ["serverAdminAuthMode() === 'netty-token'", "legacyNettyFetch", "OPENDISPATCH_LEGACY_ADMIN_TOKEN"],
    )
    require(
        "ai-event-gateway-admin-ui/app/api/auth/[...path]/route.ts",
        ["proxyAdminAuth"],
    )

    require(
        "docs/P4_A_CORE_ADMIN_AUTHENTICATION/README.md",
        ["Core server-side session", "GET /api/auth/csrf", "P4-B will switch"],
    )
    require(
        "docs/P4_A_CORE_ADMIN_AUTHENTICATION/data-flow.md",
        ["Spring Session Redis", "selectedTenantId (authoritative)", "constant-time token verification"],
    )
    require(
        "docs/P4_A_CORE_ADMIN_AUTHENTICATION/next-phase.md",
        ["Remove access and refresh tokens from browser storage", "P4-C removes Netty human login"],
    )

    for path, markers in {
        "ai-event-gateway-core/identity-access/src/test/java/com/opensocket/aievent/core/identity/AdminPrincipalTest.java": ["exposesRolesPermissionsAndTenantSelection"],
        "ai-event-gateway-core/identity-access/src/test/java/com/opensocket/aievent/core/identity/ConfiguredAdminIdentityRepositoryTest.java": ["failsClosedWhenEnabledWithoutCredential", "failsClosedWhenPasswordHashDoesNotUseDelegatingFormat", "encodesExplicitLocalPlaintextOnlyOnceAtStartup", "permitsBootstrapAdapterToBeDisabledForFutureDatabaseOrIdpReplacement"],
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/security/CoreInternalTokenVerifierTest.java": ["leavesCoreHumanLoginOutsideMachineTokenClassification", "usesDedicatedEventIntakeCredential"],
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/security/CoreInternalTokenAuthenticationFilterTest.java": ["recoveryApproverDoesNotInheritRecoveryAdminOrRecoveryOperatorAuthority", "existingHumanSessionIsNotOverwrittenByMachineTokenFilter"],
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/identity/service/AdminAuthenticationServiceTest.java": ["createsServerSideSessionAndPersistsSelectedTenant", "rejectsMachinePrincipalForHumanIdentityEndpoints"],
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/http/context/OpenDispatchRequestContextFilterTest.java": ["shouldUseSessionSelectedTenantInsteadOfUntrustedHeaderOrQueryParameter"],
    }.items():
        require(path, markers)

    print("[PASS] P4-A Core human authentication, Redis-backed session, CSRF, tenant authority, and dual-track compatibility")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
