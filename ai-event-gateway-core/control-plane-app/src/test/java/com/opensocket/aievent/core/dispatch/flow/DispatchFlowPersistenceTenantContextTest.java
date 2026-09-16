package com.opensocket.aievent.core.dispatch.flow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;

class DispatchFlowPersistenceTenantContextTest {

    @AfterEach
    void cleanup() {
        IamTenantContextHolder.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void bindsAuthenticatedTenantAndActorToTransactionLocalPostgresContext() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("tenant-a:user-a");
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try (IamTenantContextHolder.Scope ignored = IamTenantContextHolder.open(
                new IamTenantExecutionContext("tenant-a", "user-a"))) {
            DispatchFlowPersistenceTenantContext.bind(jdbc, "tenant-a", "dispatch-flow-management");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(
                contains("set_config('app.current_tenant_id'"), params.capture(), eq(String.class));
        org.assertj.core.api.Assertions.assertThat(params.getValue().getValue("tenantId")).isEqualTo("tenant-a");
        org.assertj.core.api.Assertions.assertThat(params.getValue().getValue("actorId")).isEqualTo("user-a");
    }

    @Test
    void rejectsCrossTenantMutationBeforeAnyDatabaseContextIsBound() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try (IamTenantContextHolder.Scope ignored = IamTenantContextHolder.open(
                new IamTenantExecutionContext("tenant-a", "user-a"))) {
            assertThatThrownBy(() -> DispatchFlowPersistenceTenantContext.bind(
                    jdbc, "tenant-b", "dispatch-flow-management"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TENANT_CONTEXT_MISMATCH");
        }

        verify(jdbc, never()).queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(String.class));
    }

    @Test
    void failsClosedWhenATransactionBoundaryIsMissing() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);

        assertThatThrownBy(() -> DispatchFlowPersistenceTenantContext.bind(
                jdbc, "tenant-a", "dispatch-flow-management"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_TRANSACTION_REQUIRED");

        verify(jdbc, never()).queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(String.class));
    }
}
