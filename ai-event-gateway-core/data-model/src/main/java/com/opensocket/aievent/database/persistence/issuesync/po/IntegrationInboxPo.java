package com.opensocket.aievent.database.persistence.issuesync.po;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor; import java.time.OffsetDateTime;
@Getter @Setter @NoArgsConstructor
public class IntegrationInboxPo {
 private String tenantId;
 private String inboxId;
 private String connectionId;
 private String providerType;
 private String providerEventId;
 private String eventType;
 private String externalProjectId;
 private String externalIssueId;
 private boolean signatureVerified;
 private String payloadJson;
 private String payloadHash;
 private String status;
 private int replayCount;
 private OffsetDateTime receivedAt;
 private OffsetDateTime processedAt;
 private String lastErrorCode;
 private String lastErrorMessage;
 private String correlationId;
}
