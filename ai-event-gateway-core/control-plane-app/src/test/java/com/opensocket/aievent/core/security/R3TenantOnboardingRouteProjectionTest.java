package com.opensocket.aievent.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opensocket.aievent.core.iam.api.security.IamSecurityAdapter;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision;
import com.opensocket.aievent.core.iam.security.contract.AuthorizationRequest;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class R3TenantOnboardingRouteProjectionTest {
    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

    @Test
    void rootTenantOnboardingRouteProjectsBeforeTenantAuthorization() {
        var resolved = new R3HumanApiPermissionRegistry().resolve(
                "POST", "/api/admin/access/tenants/tenant-a/user-onboarding").orElseThrow();
        AuthenticationContext root = new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.INSTANCE_ROOT, "root"),
                new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root"),
                TenantRef.instance(), Optional.empty(), AuthenticationAssurance.passwordOnly(NOW),
                new SecurityEpoch(11, 0, 7), Optional.empty(), NOW, NOW.plusSeconds(3600));
        AuthenticationContext projected = R4RouteScopedAuthenticationContext.project(
                root, resolved.rule().scopeType(), resolved.pathVariables()).orElseThrow();
        AtomicReference<AuthorizationRequest> captured = new AtomicReference<>();
        IamSecurityAdapter adapter = new IamSecurityAdapter(request -> {
            captured.set(request);
            return new AuthorizationDecision(
                    "decision-onboarding", AuthorizationDecision.Effect.ALLOW,
                    "AUTH_PERMISSION_GRANTED", Set.of(), Set.of(),
                    request.requestedScopeType(), request.requestedScopeId(),
                    SecurityEpoch.ZERO, NOW);
        }, null, Clock.fixed(NOW, ZoneOffset.UTC));

        adapter.authorize(
                projected,
                resolved.rule().permission(),
                resolved.rule().resourceType(),
                resolved.resourceId(),
                resolved.rule().scopeType(),
                projected.activeTenant().tenantId(),
                Map.of("routePattern", resolved.rule().routeTemplate()));

        assertEquals(TenantRef.tenant("tenant-a"), projected.activeTenant());
        assertEquals("tenant-a", captured.get().activeTenant().tenantId());
        assertEquals("tenant-a", captured.get().requestedScopeId());
        assertEquals("identity.user.create", captured.get().permission());
    }
}
