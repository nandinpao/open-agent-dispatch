package com.opensocket.aievent.core.assignment;

/**
 * RS4 one-way hook that refreshes Resource Access descriptor/participant evidence after Task assignment.
 * Task orchestration owns no Resource Access policy and cannot grant permission through this port.
 */
@FunctionalInterface
public interface TaskAuthorizationProjectionPort {
    void projectTask(String tenantId, String taskId, String correlationId);

    static TaskAuthorizationProjectionPort noop() { return (tenantId, taskId, correlationId) -> {}; }
}
