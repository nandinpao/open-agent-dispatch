package com.opensocket.aievent.database.persistence.issuesync.po;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor; import java.time.OffsetDateTime;
@Getter @Setter @NoArgsConstructor
public class IntegrationDeadLetterPo {
 private String tenantId;
 private String deadLetterId;
 private String outboxId;
 private String taskId;
 private String taskIssueLinkId;
 private String operationType;
 private String payloadJson;
 private String payloadHash;
 private String failureCode;
 private String failureMessage;
 private int attemptCount;
 private String status;
 private OffsetDateTime openedAt;
 private OffsetDateTime retriedAt;
 private OffsetDateTime resolvedAt;
 private String resolvedBy;
 private String resolutionReason;
 private String correlationId;
}
