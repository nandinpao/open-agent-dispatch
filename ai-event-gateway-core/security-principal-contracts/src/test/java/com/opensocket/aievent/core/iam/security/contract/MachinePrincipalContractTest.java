package com.opensocket.aievent.core.iam.security.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MachinePrincipalContractTest {
    @Test
    void tenantMachinePrincipalProjectsToExistingServiceAccountAuthorizationClass() {
        MachinePrincipal principal = new MachinePrincipal(
                "svc-erp-prod",
                MachinePrincipalType.INTEGRATION,
                TenantRef.tenant("tenant-a")
        );

        assertEquals(PrincipalRef.PrincipalType.SERVICE_ACCOUNT,
                principal.authorizationPrincipalRef().principalType());
        assertEquals(SubjectRef.IdentityType.SERVICE_ACCOUNT, principal.subjectRef().identityType());
        assertTrue(principal.tenantBound());
    }

    @Test
    void nonSystemMachinePrincipalCannotUseInstanceScope() {
        assertThrows(IllegalArgumentException.class, () -> new MachinePrincipal(
                "svc-erp-prod",
                MachinePrincipalType.SERVICE_ACCOUNT,
                TenantRef.instance()
        ));
    }

    @Test
    void accessBoundaryIsFailClosedAndChecksExplicitBounds() {
        MachineAccessBoundary boundary = new MachineAccessBoundary(
                Set.of("events.intake"),
                Set.of("events.write"),
                Set.of("opendispatch-event-api"),
                Set.of("ERP-TW-PROD"),
                Set.of("/api/events/"),
                Set.of("10.0.0.0/8"),
                Map.of("department", "manufacturing")
        );

        assertTrue(boundary.permitsPermissionBound("events.intake"));
        assertTrue(boundary.permitsScope("events.write"));
        assertTrue(boundary.permitsAudience("opendispatch-event-api"));
        assertTrue(boundary.permitsSourceSystem("ERP-TW-PROD"));
        assertTrue(boundary.permitsApiPath("/api/events/intake"));
        assertFalse(boundary.permitsSourceSystem("MES-TW-PROD"));
        assertFalse(MachineAccessBoundary.denyAll().permitsApiPath("/api/events/intake"));
    }

    @Test
    void machineAuthenticationContextKeepsMachineBoundaryOutOfLegacyAuthenticationContext() {
        Instant issued = Instant.parse("2026-08-13T00:00:00Z");
        MachineAuthenticationContext context = new MachineAuthenticationContext(
                new MachinePrincipal("svc-erp-prod", MachinePrincipalType.SERVICE_ACCOUNT,
                        TenantRef.tenant("tenant-a")),
                MachineCredentialRef.accessToken("token-1"),
                new MachineAccessBoundary(Set.of("events.intake"), Set.of(),
                        Set.of("opendispatch-event-api"), Set.of(), Set.of("/api/events/"), Set.of(), Map.of()),
                new AuthenticationAssurance(AuthenticationAssurance.Level.SYSTEM,
                        Set.of("SERVICE_ACCOUNT_ACCESS_TOKEN"), issued.plusSeconds(1)),
                SecurityEpoch.ZERO,
                issued,
                issued.plusSeconds(900)
        );

        AuthenticationContext legacy = context.toAuthenticationContext();
        assertEquals("svc-erp-prod", legacy.principal().principalId());
        assertEquals("tenant-a", legacy.activeTenant().tenantId());
        assertTrue(legacy.session().isEmpty());
        assertEquals(Set.of("events.intake"), context.accessBoundary().permissionBounds());
    }
    @Test
    void agentMachinePrincipalIsNotMasqueradedAsServiceAccount() {
        MachinePrincipal principal = new MachinePrincipal(
                "agent-finance-01", MachinePrincipalType.AGENT, TenantRef.tenant("tenant-a"));
        assertEquals(PrincipalRef.PrincipalType.AGENT, principal.authorizationPrincipalRef().principalType());
        assertEquals(SubjectRef.IdentityType.AGENT, principal.subjectRef().identityType());
    }

    @Test
    void delegationChainPreservesOriginAndRejectsCrossTenantHop() {
        MachinePrincipal origin = new MachinePrincipal("svc-erp", MachinePrincipalType.SERVICE_ACCOUNT, TenantRef.tenant("tenant-a"));
        MachinePrincipal agent = new MachinePrincipal("agent-a", MachinePrincipalType.AGENT, TenantRef.tenant("tenant-a"));
        MachinePrincipal a2a = new MachinePrincipal("agent-b", MachinePrincipalType.A2A_AGENT, TenantRef.tenant("tenant-a"));
        MachineDelegationContext chain = MachineDelegationContext.origin(origin)
                .delegateTo(agent, "dispatch", "TASK", "task-1", Instant.parse("2026-08-13T00:00:01Z"))
                .delegateTo(a2a, "a2a", "A2A_REQUEST", "req-1", Instant.parse("2026-08-13T00:00:02Z"));
        assertEquals(origin, chain.originPrincipal());
        assertEquals(agent, chain.delegatingPrincipal());
        assertEquals(a2a, chain.executingPrincipal());
        assertEquals(2, chain.hops().size());
        MachinePrincipal otherTenant = new MachinePrincipal("agent-x", MachinePrincipalType.AGENT, TenantRef.tenant("tenant-b"));
        assertThrows(IllegalArgumentException.class, () -> chain.delegateTo(
                otherTenant, "bad", "TASK", "task-2", Instant.parse("2026-08-13T00:00:03Z")));
    }

}
