package com.opensocket.aievent.core.a2a.api;

import com.opensocket.aievent.core.iam.security.contract.MachineExecutionContext;

/** Authenticated request metadata supplied by the executable HTTP assembly. */
public record A2AApiRequestContext(String tenantId, String actorId, String correlationId,
                                   MachineExecutionContext machineExecutionContext) {
    public A2AApiRequestContext(String tenantId, String actorId, String correlationId) {
        this(tenantId, actorId, correlationId, null);
    }
}
