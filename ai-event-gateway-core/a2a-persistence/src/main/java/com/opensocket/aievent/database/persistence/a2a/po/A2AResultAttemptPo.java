package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class A2AResultAttemptPo {
 private String tenantId,attemptId,requestId,childTaskId,resultId,assignmentId,executionAttemptId,dispatchRequestId,callbackInboxId,agentId,agentSessionId,resultStatus,decision,classification,resultEvidenceHash,resultFingerprint,policySnapshotHash,reasonCode,reason,payloadHash,idempotencyKey,correlationId;
 private Integer attemptNo; private int resultSchemaVersion; private long policyVersion; private OffsetDateTime occurredAt,receivedAt;
}
