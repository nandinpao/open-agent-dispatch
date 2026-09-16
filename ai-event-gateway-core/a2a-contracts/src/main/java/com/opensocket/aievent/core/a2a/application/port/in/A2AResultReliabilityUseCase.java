package com.opensocket.aievent.core.a2a.application.port.in;

import com.opensocket.aievent.core.a2a.A2AResultProcessing;
import com.opensocket.aievent.core.a2a.A2AResultReliabilitySnapshot;

/** Inbound boundary for operational inspection and governed Result reconciliation. */
public interface A2AResultReliabilityUseCase {
    A2AResultReliabilitySnapshot reliability(String tenantId, String taskId, int limit);
    A2AResultProcessing reconcile(String tenantId, String resultId, String actorId, String reason, String idempotencyKey);
}
