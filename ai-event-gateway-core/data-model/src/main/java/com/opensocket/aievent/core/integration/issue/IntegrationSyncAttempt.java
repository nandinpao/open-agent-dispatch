package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record IntegrationSyncAttempt(String tenantId,String attemptId,String outboxId,int attemptNo,String workerId,String status,
 Integer providerStatus,String requestPayloadHash,String responseSummary,boolean retryable,String errorCode,String errorMessage,
 OffsetDateTime startedAt,OffsetDateTime completedAt,String correlationId) {}
