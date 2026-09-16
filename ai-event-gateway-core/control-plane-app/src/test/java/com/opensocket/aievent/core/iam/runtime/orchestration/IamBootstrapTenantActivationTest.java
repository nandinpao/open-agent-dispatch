package com.opensocket.aievent.core.iam.runtime.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.authentication.application.port.in.MfaAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.RootAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.SessionCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.RootBootstrapStateRepository;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.organization.application.command.ChangeTenantStatusCommand;
import com.opensocket.aievent.core.iam.organization.application.port.in.OrganizationQueryPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.organization.application.query.FindTenantQuery;
import com.opensocket.aievent.core.iam.organization.domain.Tenant;
import com.opensocket.aievent.core.iam.organization.domain.TenantId;
import com.opensocket.aievent.core.iam.organization.domain.TenantStatus;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IamBootstrapTenantActivationTest {
    private static final Instant NOW = Instant.parse("2026-08-04T13:18:18Z");

    @Test
    void activatesProvisioningTenantBeforeInitialPasswordTokenEligibility() {
        TenantCommandPort tenantCommands = mock(TenantCommandPort.class);
        OrganizationQueryPort tenantQueries = mock(OrganizationQueryPort.class);
        Tenant provisioning = provisioningTenant();
        Tenant active = provisioning.changeStatus(TenantStatus.ACTIVE, "root", NOW.plusSeconds(1));
        when(tenantQueries.findTenant(new FindTenantQuery("tenant-a"))).thenReturn(Optional.of(provisioning));
        when(tenantCommands.changeTenantStatus(any(ChangeTenantStatusCommand.class))).thenReturn(active);

        Tenant result = orchestrator(tenantCommands, tenantQueries).activateBootstrapTenant("tenant-a", context());

        assertEquals(TenantStatus.ACTIVE, result.status());
        ArgumentCaptor<ChangeTenantStatusCommand> command = ArgumentCaptor.forClass(ChangeTenantStatusCommand.class);
        verify(tenantCommands).changeTenantStatus(command.capture());
        assertEquals("tenant-a", command.getValue().tenantId());
        assertEquals(TenantStatus.ACTIVE, command.getValue().targetStatus());
        assertEquals(provisioning.version(), command.getValue().expectedVersion());
        assertEquals("root", command.getValue().actorId());
    }

    @Test
    void leavesAnAlreadyActiveBootstrapTenantUnchanged() {
        TenantCommandPort tenantCommands = mock(TenantCommandPort.class);
        OrganizationQueryPort tenantQueries = mock(OrganizationQueryPort.class);
        Tenant active = provisioningTenant().changeStatus(TenantStatus.ACTIVE, "root", NOW.plusSeconds(1));
        when(tenantQueries.findTenant(new FindTenantQuery("tenant-a"))).thenReturn(Optional.of(active));

        Tenant result = orchestrator(tenantCommands, tenantQueries).activateBootstrapTenant("tenant-a", context());

        assertEquals(TenantStatus.ACTIVE, result.status());
        verify(tenantCommands, never()).changeTenantStatus(any(ChangeTenantStatusCommand.class));
    }

    private static IamBootstrapRuntimeOrchestrator orchestrator(
            TenantCommandPort tenantCommands,
            OrganizationQueryPort tenantQueries) {
        return new IamBootstrapRuntimeOrchestrator(
                mock(RootBootstrapStateRepository.class),
                mock(IdentityCommandPort.class),
                mock(IdentityQueryPort.class),
                mock(MfaAuthenticationCommandPort.class),
                mock(RootAuthenticationCommandPort.class),
                mock(SessionCommandPort.class),
                mock(BrowserSessionRepository.class),
                tenantCommands,
                tenantQueries,
                mock(RbacAdministrationPort.class),
                mock(AccessTokenCommandPort.class),
                mock(IamOneTimeSecretDeliveryPort.class),
                mock(IamIdempotencyExecutor.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Tenant provisioningTenant() {
        return Tenant.provision(
                new TenantId("tenant-a"),
                "TENANT-A",
                "TENANT-A",
                "TENANT-A",
                ZoneId.of("Asia/Taipei"),
                Locale.forLanguageTag("en-US"),
                "TW",
                "root",
                NOW);
    }

    private static IamApiRequestContext context() {
        return new IamApiRequestContext(
                Optional.empty(),
                "correlation-fix18",
                "idempotency-fix18",
                "",
                "127.0.0.1",
                "fix18-test",
                NOW,
                java.util.Set.of());
    }
}
