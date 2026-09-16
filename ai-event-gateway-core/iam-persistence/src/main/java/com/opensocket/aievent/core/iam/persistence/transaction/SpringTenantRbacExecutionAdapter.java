package com.opensocket.aievent.core.iam.persistence.transaction;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.rbac.application.port.out.TenantRbacExecutionPort;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring transaction boundary for Tenant-scoped RBAC API services. */
public final class SpringTenantRbacExecutionAdapter implements TenantRbacExecutionPort {
    private final TransactionTemplate readOnly;
    private final TransactionTemplate write;

    public SpringTenantRbacExecutionAdapter(PlatformTransactionManager manager) {
        Objects.requireNonNull(manager, "manager");
        this.readOnly = new TransactionTemplate(manager);
        this.readOnly.setReadOnly(true);
        this.readOnly.setName("iam-rbac-api:read");
        this.write = new TransactionTemplate(manager);
        this.write.setName("iam-rbac-api:write");
    }

    @Override
    public <T> T read(String tenantId, String actorId, Supplier<T> work) {
        return execute(readOnly, tenantId, actorId, work);
    }

    @Override
    public <T> T write(String tenantId, String actorId, Supplier<T> work) {
        return execute(write, tenantId, actorId, work);
    }

    private <T> T execute(
            TransactionTemplate template,
            String tenantId,
            String actorId,
            Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        String tenant = normalizeTenant(tenantId);
        String actor = normalizeActor(actorId);
        return IamTenantContextHolder.withContext(
                new IamTenantExecutionContext(tenant, actor),
                () -> template.execute(status -> work.get()));
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "INSTANCE" : tenantId.trim();
    }

    private String normalizeActor(String actorId) {
        return actorId == null || actorId.isBlank() ? "iam-rbac-api" : actorId.trim();
    }
}
