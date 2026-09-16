package com.opensocket.aievent.core.iam.token.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opensocket.aievent.core.iam.security.contract.MachinePrincipalType;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.token.application.result.ValidatedTokenResult;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import com.opensocket.aievent.core.iam.token.domain.ServiceAccount;
import com.opensocket.aievent.core.iam.token.domain.ServiceAccountId;
import com.opensocket.aievent.core.iam.token.domain.TokenScope;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceAccountMachineAuthenticationFactoryTest {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    void projectsValidatedServiceAccountTokenIntoCanonicalMachineContext() {
        ServiceAccount account = account("tenant-a", "svc-erp-prod");
        TokenScope effective = new TokenScope(
                Set.of("events.intake"),
                Set.of("opendispatch-event-api"),
                Set.of("/api/events/"),
                Set.of("10.0.0.0/8")
        );
        ValidatedTokenResult token = new ValidatedTokenResult(
                "sat-1",
                AccessTokenType.SERVICE_ACCOUNT_TOKEN,
                new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT, "svc-erp-prod"),
                "tenant-a",
                effective,
                new SecurityEpoch(2, 3, 4),
                NOW,
                NOW.plusSeconds(900)
        );

        var context = ServiceAccountMachineAuthenticationFactory.fromValidatedToken(
                account,
                token,
                NOW.plusSeconds(10)
        );

        assertEquals(MachinePrincipalType.SERVICE_ACCOUNT, context.principal().principalType());
        assertEquals("tenant-a", context.principal().activeTenant().tenantId());
        assertEquals("sat-1", context.credential().tokenId());
        assertTrue(context.accessBoundary().permitsPermissionBound("events.intake"));
        assertTrue(context.accessBoundary().permitsScope("events.write"));
        assertTrue(context.accessBoundary().permitsAudience("opendispatch-event-api"));
        assertTrue(context.accessBoundary().permitsSourceSystem("ERP-TW-PROD"));
        assertTrue(context.accessBoundary().permitsApiPath("/api/events/intake"));
    }

    @Test
    void rejectsCrossTenantProjection() {
        ServiceAccount account = account("tenant-a", "svc-erp-prod");
        ValidatedTokenResult token = new ValidatedTokenResult(
                "sat-1",
                AccessTokenType.SERVICE_ACCOUNT_TOKEN,
                new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT, "svc-erp-prod"),
                "tenant-b",
                new TokenScope(Set.of("events.intake"), Set.of("event-api"), Set.of("/api/events/"), Set.of("10.0.0.0/8")),
                SecurityEpoch.ZERO,
                NOW,
                NOW.plusSeconds(900)
        );

        assertThrows(IllegalArgumentException.class, () ->
                ServiceAccountMachineAuthenticationFactory.fromValidatedToken(
                        account, token, NOW.plusSeconds(1)));
    }

    private static ServiceAccount account(String tenantId, String serviceAccountId) {
        return ServiceAccount.create(
                tenantId,
                new ServiceAccountId(serviceAccountId),
                "ERP Production",
                "ERP integration",
                "owner-user",
                "department-it",
                new TokenScope(
                        Set.of("events.intake"),
                        Set.of("opendispatch-event-api"),
                        Set.of("/api/events/"),
                        Set.of("10.0.0.0/8")
                ),
                Set.of("events.write"),
                Set.of("ERP-TW-PROD"),
                Duration.ofHours(1),
                3,
                Duration.ofDays(180),
                2,
                300,
                NOW.plus(Duration.ofDays(30)),
                "root",
                NOW
        );
    }
}
