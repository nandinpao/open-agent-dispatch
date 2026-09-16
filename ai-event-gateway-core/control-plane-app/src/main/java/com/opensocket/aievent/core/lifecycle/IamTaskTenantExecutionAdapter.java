package com.opensocket.aievent.core.lifecycle;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.task.TaskTenantExecutionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Tenant-aware transaction boundary for scheduler-driven Task mutations. */
@Component
public final class IamTaskTenantExecutionAdapter implements TaskTenantExecutionPort {
    private final TransactionTemplate transactions;

    public IamTaskTenantExecutionAdapter(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T execute(String tenantId, String actorId, Supplier<T> work) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required for background Task mutation");
        }
        String actor = actorId == null || actorId.isBlank() ? "core-task-background" : actorId.trim();
        return IamTenantContextHolder.withContext(
                new IamTenantExecutionContext(tenantId.trim(), actor),
                () -> transactions.execute(status -> work.get()));
    }
}
