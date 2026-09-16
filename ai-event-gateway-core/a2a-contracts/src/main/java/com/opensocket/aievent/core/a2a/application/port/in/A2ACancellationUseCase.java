package com.opensocket.aievent.core.a2a.application.port.in;

import java.util.List;

import com.opensocket.aievent.core.a2a.A2ACancellationEvidence;
import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.a2a.A2AReconciliationCase;
import com.opensocket.aievent.core.a2a.A2AReconciliationStatus;
import com.opensocket.aievent.core.a2a.A2ARequest;

/** Inbound boundary for A2A cancellation and operational reconciliation. */
public interface A2ACancellationUseCase {
    A2ARequest request(String tenantId, String requestId, String actorType, String actorId,
                       String reason, String idempotencyKey);
    A2ACancellationRecord get(String tenantId, String cancellationId);
    List<A2ACancellationEvidence> evidence(String tenantId, String cancellationId, int limit);
    List<A2AReconciliationCase> openCases(String tenantId, int limit);
    A2AReconciliationCase resolveCase(String tenantId, String caseId, A2AReconciliationStatus status,
                                      String actorId, String reason);
}
