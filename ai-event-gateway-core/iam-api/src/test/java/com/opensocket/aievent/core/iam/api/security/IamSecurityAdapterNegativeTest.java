package com.opensocket.aievent.core.iam.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opensocket.aievent.core.iam.rbac.application.port.in.AuthorizationPort;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
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

class IamSecurityAdapterNegativeTest {
    private static final Instant NOW = Instant.parse("2026-07-23T04:00:00Z");

    @Test
    void forgedTenantScopeCannotReplaceAuthenticatedTenant() {
        AtomicReference<AuthorizationRequest> captured = new AtomicReference<>();
        AuthorizationPort authorization = request -> {
            captured.set(request);
            return allow(request);
        };
        IamSecurityAdapter adapter = new IamSecurityAdapter(
                authorization, null, Clock.fixed(NOW, ZoneOffset.UTC));

        adapter.authorize(authentication("tenant-a"), "identity.user.read", "USER", "user-2",
                ScopeType.TENANT, "tenant-b", Map.of());

        assertEquals("tenant-a", captured.get().activeTenant().tenantId());
        assertEquals("tenant-a", captured.get().requestedScopeId());
    }

    @Test
    void tenantUserCannotEscalateToInstanceScope() {
        IamSecurityAdapter adapter = new IamSecurityAdapter(
                request -> allow(request), null, Clock.fixed(NOW, ZoneOffset.UTC));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                adapter.authorize(authentication("tenant-a"), "instance.tenant.manage", "TENANT", "tenant-b",
                        ScopeType.INSTANCE, "INSTANCE", Map.of()));

        assertEquals("AUTH_TENANT_MISMATCH", error.getMessage());
    }

    @Test
    void deniedDecisionNeverBecomesSuccessfulManagementOperation() {
        IamSecurityAdapter adapter = new IamSecurityAdapter(
                request -> new AuthorizationDecision("decision-deny", AuthorizationDecision.Effect.DENY,
                        "AUTH_PERMISSION_DENIED", Set.of(), Set.of(), request.requestedScopeType(),
                        request.requestedScopeId(), SecurityEpoch.ZERO, NOW),
                null, Clock.fixed(NOW, ZoneOffset.UTC));

        IamAuthorizationException error = assertThrows(IamAuthorizationException.class, () ->
                adapter.require(authentication("tenant-a"), "identity.tenant_role.manage", "ROLE", "role-1",
                        ScopeType.TENANT, "tenant-a", Map.of()));

        assertEquals("AUTH_PERMISSION_DENIED", error.decision().reasonCode());
    }

    private static AuthenticationContext authentication(String tenantId) {
        return new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, "user-1"),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-1"),
                TenantRef.tenant(tenantId),
                Optional.empty(),
                AuthenticationAssurance.passwordOnly(NOW),
                SecurityEpoch.ZERO,
                Optional.empty(),
                NOW,
                NOW.plusSeconds(3600));
    }

    private static AuthorizationDecision allow(AuthorizationRequest request) {
        return new AuthorizationDecision("decision-allow", AuthorizationDecision.Effect.ALLOW,
                "AUTH_PERMISSION_GRANTED", Set.of(), Set.of(), request.requestedScopeType(),
                request.requestedScopeId(), SecurityEpoch.ZERO, NOW);
    }
}
