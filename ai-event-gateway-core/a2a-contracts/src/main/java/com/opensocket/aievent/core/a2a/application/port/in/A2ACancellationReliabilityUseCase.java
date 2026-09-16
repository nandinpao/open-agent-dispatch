package com.opensocket.aievent.core.a2a.application.port.in;

import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.a2a.A2ACancellationReliabilitySnapshot;

public interface A2ACancellationReliabilityUseCase {
    A2ACancellationReliabilitySnapshot reliability(String tenantId, String cancellationId, int limit);
    A2ACancellationRecord reconcile(String tenantId, String cancellationId, String actorId,
                                    String reason, String idempotencyKey);
}
