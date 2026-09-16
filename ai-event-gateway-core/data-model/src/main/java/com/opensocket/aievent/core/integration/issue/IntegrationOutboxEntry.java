package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record IntegrationOutboxEntry(String tenantId,String outboxId,String aggregateType,String aggregateId,String taskId,
 String taskIssueLinkId,String issueRelationshipId,String connectionId,String projectMappingId,IntegrationOperationType operationType,
 String eventType,String payloadJson,String payloadHash,String idempotencyKey,IntegrationOutboxStatus status,int priority,int attemptCount,
 int maxAttempts,OffsetDateTime nextAttemptAt,String claimedBy,OffsetDateTime claimUntil,String lastErrorCode,String lastErrorMessage,
 String correlationId,String causationId,OffsetDateTime createdAt,OffsetDateTime updatedAt,OffsetDateTime completedAt) {}
