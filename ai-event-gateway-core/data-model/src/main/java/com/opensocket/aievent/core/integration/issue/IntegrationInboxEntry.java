package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record IntegrationInboxEntry(String tenantId,String inboxId,String connectionId,String providerType,String providerEventId,
 String eventType,String externalProjectId,String externalIssueId,boolean signatureVerified,String payloadJson,String payloadHash,
 IntegrationInboxStatus status,int replayCount,OffsetDateTime receivedAt,OffsetDateTime processedAt,String lastErrorCode,
 String lastErrorMessage,String correlationId) {}
