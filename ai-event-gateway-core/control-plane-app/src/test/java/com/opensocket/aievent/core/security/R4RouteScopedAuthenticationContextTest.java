package com.opensocket.aievent.core.security;

import static org.junit.jupiter.api.Assertions.*;

import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.core.iam.security.contract.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class R4RouteScopedAuthenticationContextTest {
    private static final Instant NOW = Instant.parse("2026-08-06T09:00:00Z");

    @Test
    void rootTenantProjectionPreservesInstanceSessionEpoch() {
        SecurityEpoch instanceEpoch = new SecurityEpoch(11, 0, 7);
        AuthenticationContext root = new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.INSTANCE_ROOT, "root"),
                new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root"),
                TenantRef.instance(), Optional.empty(), AuthenticationAssurance.passwordOnly(NOW),
                instanceEpoch, Optional.empty(), NOW, NOW.plusSeconds(3600));

        AuthenticationContext projected = R4RouteScopedAuthenticationContext.project(
                root, ScopeType.TENANT, Map.of("tenantId", "tenant-a")).orElseThrow();

        assertEquals(TenantRef.tenant("tenant-a"), projected.activeTenant());
        assertEquals(instanceEpoch, projected.securityEpoch());
        assertEquals(root.session(), projected.session());
        assertEquals(root.principal(), projected.principal());
    }

    @Test
    void rootTenantProjectionReturnsToInstanceForPlatformRoute() {
        SecurityEpoch instanceEpoch = new SecurityEpoch(11, 0, 7);
        AuthenticationContext tenantProjectedRoot = new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.INSTANCE_ROOT, "root"),
                new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root"),
                TenantRef.tenant("tenant-a"), Optional.empty(), AuthenticationAssurance.passwordOnly(NOW),
                instanceEpoch, Optional.empty(), NOW, NOW.plusSeconds(3600));

        AuthenticationContext projected = R4RouteScopedAuthenticationContext.project(
                tenantProjectedRoot, ScopeType.INSTANCE, Map.of()).orElseThrow();

        assertEquals(TenantRef.instance(), projected.activeTenant());
        assertEquals(instanceEpoch, projected.securityEpoch());
        assertEquals(tenantProjectedRoot.session(), projected.session());
        assertEquals(tenantProjectedRoot.principal(), projected.principal());
    }

    @Test
    void tenantUserCannotProjectToInstanceScope() {
        AuthenticationContext user = new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, "user-a"),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-a"),
                TenantRef.tenant("tenant-a"), Optional.empty(), AuthenticationAssurance.passwordOnly(NOW),
                new SecurityEpoch(11, 4, 2), Optional.empty(), NOW, NOW.plusSeconds(3600));

        AuthenticationContext unchanged = R4RouteScopedAuthenticationContext.project(
                user, ScopeType.INSTANCE, Map.of()).orElseThrow();
        assertEquals(TenantRef.tenant("tenant-a"), unchanged.activeTenant());
    }

    @Test
    void tenantUserCannotProjectAcrossTenants() {
        AuthenticationContext user = new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, "user-a"),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-a"),
                TenantRef.tenant("tenant-a"), Optional.empty(), AuthenticationAssurance.passwordOnly(NOW),
                new SecurityEpoch(11, 4, 2), Optional.empty(), NOW, NOW.plusSeconds(3600));

        assertTrue(R4RouteScopedAuthenticationContext.project(
                user, ScopeType.TENANT, Map.of("tenantId", "tenant-b")).isEmpty());
    }
    @Test
    void rootTenantScopedRouteWithoutPathUsesServerResolvedTenantHint() {
        SecurityEpoch instanceEpoch = new SecurityEpoch(11, 0, 7);
        AuthenticationContext root = new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.INSTANCE_ROOT, "root"),
                new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root"),
                TenantRef.instance(), Optional.empty(), AuthenticationAssurance.passwordOnly(NOW),
                instanceEpoch, Optional.empty(), NOW, NOW.plusSeconds(3600));

        AuthenticationContext projected = R4RouteScopedAuthenticationContext.project(
                root, ScopeType.TENANT, Map.of(), "tenant-a").orElseThrow();

        assertEquals(TenantRef.tenant("tenant-a"), projected.activeTenant());
        assertEquals(instanceEpoch, projected.securityEpoch());
    }

    @Test
    void rootTenantScopedRouteWithoutPathOrServerHintFailsClosed() {
        AuthenticationContext root = new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.INSTANCE_ROOT, "root"),
                new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root"),
                TenantRef.instance(), Optional.empty(), AuthenticationAssurance.passwordOnly(NOW),
                SecurityEpoch.ZERO, Optional.empty(), NOW, NOW.plusSeconds(3600));

        assertTrue(R4RouteScopedAuthenticationContext.project(
                root, ScopeType.TENANT, Map.of(), "").isEmpty());
    }

}
