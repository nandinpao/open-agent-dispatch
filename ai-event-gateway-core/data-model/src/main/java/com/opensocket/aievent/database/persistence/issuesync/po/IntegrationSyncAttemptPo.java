package com.opensocket.aievent.database.persistence.issuesync.po;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor; import java.time.OffsetDateTime;
@Getter @Setter @NoArgsConstructor
public class IntegrationSyncAttemptPo {
 private String tenantId;
 private String attemptId;
 private String outboxId;
 private int attemptNo;
 private String workerId;
 private String status;
 private Integer providerStatus;
 private String requestPayloadHash;
 private String responseSummary;
 private boolean retryable;
 private String errorCode;
 private String errorMessage;
 private OffsetDateTime startedAt;
 private OffsetDateTime completedAt;
 private String correlationId;
}
