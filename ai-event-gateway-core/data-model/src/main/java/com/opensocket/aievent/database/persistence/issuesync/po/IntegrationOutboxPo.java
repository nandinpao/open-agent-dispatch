package com.opensocket.aievent.database.persistence.issuesync.po;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor; import java.time.OffsetDateTime;
@Getter @Setter @NoArgsConstructor
public class IntegrationOutboxPo {
 private String tenantId;
 private String outboxId;
 private String aggregateType;
 private String aggregateId;
 private String taskId;
 private String taskIssueLinkId;
 private String issueRelationshipId;
 private String connectionId;
 private String projectMappingId;
 private String operationType;
 private String eventType;
 private String payloadJson;
 private String payloadHash;
 private String idempotencyKey;
 private String status;
 private int priority;
 private int attemptCount;
 private int maxAttempts;
 private OffsetDateTime nextAttemptAt;
 private String claimedBy;
 private OffsetDateTime claimUntil;
 private String lastErrorCode;
 private String lastErrorMessage;
 private String correlationId;
 private String causationId;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private OffsetDateTime completedAt;
}
