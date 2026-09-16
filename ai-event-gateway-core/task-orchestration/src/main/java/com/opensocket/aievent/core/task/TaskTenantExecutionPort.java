package com.opensocket.aievent.core.task;

import java.util.function.Supplier;

/**
 * Executes background Task work inside the authoritative Tenant persistence context.
 *
 * <p>HTTP request paths already establish Tenant context at the request boundary. Background
 * schedulers do not have that boundary, so they must explicitly enter the Task's persisted Tenant
 * before mutations that can reach FORCE-RLS tables or security-epoch triggers.</p>
 */
public interface TaskTenantExecutionPort {
    <T> T execute(String tenantId, String actorId, Supplier<T> work);

    static TaskTenantExecutionPort direct() {
        return new TaskTenantExecutionPort() {
            @Override
            public <T> T execute(String tenantId, String actorId, Supplier<T> work) {
                return work.get();
            }
        };
    }
}
