package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record IntegrationDeadLetter(String tenantId,String deadLetterId,String outboxId,String taskId,String taskIssueLinkId,
 IntegrationOperationType operationType,String payloadJson,String payloadHash,String failureCode,String failureMessage,int attemptCount,
 IntegrationDeadLetterStatus status,OffsetDateTime openedAt,OffsetDateTime retriedAt,OffsetDateTime resolvedAt,String resolvedBy,
 String resolutionReason,String correlationId) {}
