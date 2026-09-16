package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class A2AResultProcessingPo {
 private String tenantId,resultId,requestId,parentTaskId,childTaskId,processingStatus,reconciliationClassification,lastErrorCode,lastError;
 private int attemptCount; private OffsetDateTime nextReconcileAt,lastReconciledAt,completedAt,createdAt,updatedAt; private long rowVersion;
}
