package com.opensocket.aievent.core.dispatch.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.routing.RoutingSimulationService;

class DispatchSimulationApplicationServiceTest {

    @AfterEach
    void cleanup() {
        IamTenantContextHolder.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void bindsTenantContextBeforeDelegatingToProductionRoutingSimulation() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        RoutingSimulationService routing = mock(RoutingSimulationService.class);
        DispatchSimulationResponse expected = new DispatchSimulationResponse();
        expected.setTenantId("tenant-a");
        when(jdbc.queryForObject(any(String.class), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(String.class)))
                .thenReturn("tenant-a:user-a");
        when(routing.simulate(any(DispatchSimulationRequest.class))).thenReturn(expected);

        DispatchSimulationRequest request = new DispatchSimulationRequest();
        request.setTenantId("tenant-a");
        try (IamTenantContextHolder.Scope ignored = IamTenantContextHolder.open(new IamTenantExecutionContext("tenant-a", "user-a"))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            DispatchSimulationResponse actual = new DispatchSimulationApplicationService(jdbc, routing).simulate(request);
            assertThat(actual).isSameAs(expected);
        }

        verify(jdbc).queryForObject(any(String.class), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(String.class));
        verify(routing).simulate(request);
    }

    @Test
    void rejectsCrossTenantSimulationBeforeRoutingRepositoryAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        RoutingSimulationService routing = mock(RoutingSimulationService.class);
        DispatchSimulationRequest request = new DispatchSimulationRequest();
        request.setTenantId("tenant-b");

        try (IamTenantContextHolder.Scope ignored = IamTenantContextHolder.open(new IamTenantExecutionContext("tenant-a", "user-a"))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            DispatchSimulationApplicationService service = new DispatchSimulationApplicationService(jdbc, routing);
            assertThatThrownBy(() -> service.simulate(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TENANT_CONTEXT_MISMATCH");
        }
    }
}
