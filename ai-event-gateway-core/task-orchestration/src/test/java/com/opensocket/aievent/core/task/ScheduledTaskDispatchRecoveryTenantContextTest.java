package com.opensocket.aievent.core.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ScheduledTaskDispatchRecoveryTenantContextTest {

    @Test
    void shouldBindTenantAndActorInsideTransactionBeforeRecoveryFacade() {
        TaskOrchestrationFacade facade = mock(TaskOrchestrationFacade.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        TaskDispatchRecoveryProperties properties = new TaskDispatchRecoveryProperties();
        properties.setWorkerId("recovery-worker");

        when(jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id", String.class))
                .thenReturn(List.of("tenant-a"));
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        when(jdbc.queryForObject(eq("select set_config('app.current_tenant_id', ?, true)"), eq(String.class), eq("tenant-a")))
                .thenReturn("tenant-a");
        when(jdbc.queryForObject(eq("select set_config('app.current_actor_id', ?, true)"), eq(String.class), eq("recovery-worker")))
                .thenReturn("recovery-worker");
        when(facade.recoverDelayedDispatches(anyInt(), any())).thenReturn(new TaskDispatchRecoveryScanResult());

        new ScheduledTaskDispatchRecovery(facade, properties, jdbc, transactionManager).recoverDelayedDispatches();

        InOrder ordered = inOrder(transactionManager, jdbc, facade);
        ordered.verify(transactionManager).getTransaction(any());
        ordered.verify(jdbc).queryForObject(eq("select set_config('app.current_tenant_id', ?, true)"), eq(String.class), eq("tenant-a"));
        ordered.verify(jdbc).queryForObject(eq("select set_config('app.current_actor_id', ?, true)"), eq(String.class), eq("recovery-worker"));
        ordered.verify(facade).recoverDelayedDispatches(anyInt(), any());
        verify(transactionManager).commit(transaction);
    }
}
