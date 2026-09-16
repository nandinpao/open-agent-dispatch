package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class A2AResultQuarantinePo {
 private String tenantId,quarantineId,attemptId,requestId,childTaskId,classification,reasonCode,reason,payloadHash,callbackInboxId,assignmentId,executionAttemptId,status,resolvedBy,resolutionReason; private OffsetDateTime quarantinedAt,resolvedAt;
}
