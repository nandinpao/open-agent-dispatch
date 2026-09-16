package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.OffsetDateTime;
public record ProviderWebhookInboxEntry(
 String tenantId,String inboxId,String connectionId,String providerType,String providerEventId,String eventType,
 String externalProjectId,String externalIssueId,String externalIssueKey,String nonce,OffsetDateTime providerTimestamp,
 boolean signatureVerified,boolean timestampVerified,boolean nonceAccepted,boolean tenantBound,boolean connectionBound,
 String payloadJson,String payloadHash,ProviderWebhookInboxStatus status,int replayCount,int retryCount,
 OffsetDateTime nextRetryAt,String claimOwner,String claimTokenHash,OffsetDateTime claimedAt,OffsetDateTime leaseUntil,
 String processingAttemptId,OffsetDateTime receivedAt,OffsetDateTime processedAt,String lastErrorCode,
 String lastErrorMessage,long version,String correlationId) {}
