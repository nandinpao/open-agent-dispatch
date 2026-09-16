package com.opensocket.aievent.core.iam.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SpringTenantRbacExecutionAdapterTest {
    private final SpringTenantRbacExecutionAdapter adapter =
            new SpringTenantRbacExecutionAdapter(new RecordingTransactionManager());

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void readCreatesOneTenantContextAndReadOnlyTransaction() {
        String result = adapter.read(" tenant-a ", " root ", () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(IamTenantContextHolder.require().tenantId()).isEqualTo("tenant-a");
            assertThat(IamTenantContextHolder.require().actorId()).isEqualTo("root");
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(IamTenantContextHolder.current()).isEmpty();
    }

    @Test
    void writeCreatesWritableTransactionAndNormalizesInstanceScope() {
        String result = adapter.write(" ", " ", () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(IamTenantContextHolder.require().tenantId()).isEqualTo("INSTANCE");
            assertThat(IamTenantContextHolder.require().actorId()).isEqualTo("iam-rbac-api");
            return "written";
        });

        assertThat(result).isEqualTo("written");
        assertThat(IamTenantContextHolder.current()).isEmpty();
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // AbstractPlatformTransactionManager publishes the synchronized state.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
