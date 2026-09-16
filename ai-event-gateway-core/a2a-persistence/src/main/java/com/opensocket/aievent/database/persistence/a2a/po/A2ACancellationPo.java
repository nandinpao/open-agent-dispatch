package com.opensocket.aievent.database.persistence.a2a.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2ACancellationPo {
    private String tenantId,cancellationId,requestId,childTaskId,assignmentId,executionAttemptId,
            dispatchRequestId,agentId,agentSessionId,ownerGatewayNodeId,revokedFencingTokenHash,
            activeFencingTokenHash,cancellationFingerprint,idempotencyKey,status,outcome,
            processingStatus,reconciliationClassification,reason,requestedByType,requestedById,
            deliveryStatus,lastError;
    private Integer attemptNo,retryCount,reconciliationCount;
    private OffsetDateTime requestedAt,resultCutoffAt,deliveryAt,acknowledgedAt,deadlineAt,
            nextReconcileAt,lastReconciledAt,completedAt,updatedAt;
    private long version;
}
