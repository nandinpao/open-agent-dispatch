package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AResultProcessing {
    private String tenantId;
    private String resultId;
    private String requestId;
    private String parentTaskId;
    private String childTaskId;
    private A2AResultProcessingStatus processingStatus = A2AResultProcessingStatus.CHILD_COMPLETION_PENDING;
    private A2AResultReconciliationClassification reconciliationClassification = A2AResultReconciliationClassification.NONE;
    private String lastErrorCode;
    private String lastError;
    private int attemptCount;
    private OffsetDateTime nextReconcileAt;
    private OffsetDateTime lastReconciledAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private long rowVersion = 1L;
}
