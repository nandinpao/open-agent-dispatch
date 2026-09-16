package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record ProjectionProviderAttemptEvidence(
 String tenantId,String attemptId,String outboxId,String projectionId,int attemptNo,long operationSequence,
 long projectionVersion,String canonicalDocumentHash,int mappingVersion,String credentialVersion,
 String providerRequestFingerprint,String externalIdempotencyMarker,String workerId,String status,
 Integer providerStatus,boolean retryable,String errorCode,String responseSummary,OffsetDateTime retryAfterAt,
 OffsetDateTime startedAt,OffsetDateTime completedAt,String correlationId) {}
