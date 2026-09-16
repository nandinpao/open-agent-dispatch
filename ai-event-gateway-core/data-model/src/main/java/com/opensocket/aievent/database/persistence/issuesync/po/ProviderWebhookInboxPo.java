package com.opensocket.aievent.database.persistence.issuesync.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class ProviderWebhookInboxPo {
 private String tenantId,inboxId,connectionId,providerType,providerEventId,eventType,externalProjectId,externalIssueId,externalIssueKey,nonce;
 private OffsetDateTime providerTimestamp; private boolean signatureVerified,timestampVerified,nonceAccepted,tenantBound,connectionBound;
 private String payloadJson,payloadHash,status; private int replayCount,retryCount; private OffsetDateTime nextRetryAt;
 private String claimOwner,claimTokenHash; private OffsetDateTime claimedAt,leaseUntil; private String processingAttemptId;
 private OffsetDateTime receivedAt,processedAt; private String lastErrorCode,lastErrorMessage; private long version; private String correlationId;
}
