package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record ProjectionOutboxReliability(
 String tenantId,String outboxId,String projectionId,long operationSequence,long projectionVersion,
 String canonicalDocumentHash,int mappingVersion,String credentialVersion,String providerRequestFingerprint,
 ProjectionOutboxReliabilityStatus status,String claimOwner,String claimTokenHash,OffsetDateTime claimUntil,
 OffsetDateTime heartbeatAt,long rowVersion,OffsetDateTime retryAfterAt,OffsetDateTime rateLimitResetAt,
 String verificationMode,String externalIdempotencyMarker,String lastAttemptId,String lastErrorCode,
 String lastErrorMessage,OffsetDateTime sentAt,OffsetDateTime acknowledgedAt,OffsetDateTime createdAt,OffsetDateTime updatedAt) {
 public boolean terminal(){return status==ProjectionOutboxReliabilityStatus.ACKNOWLEDGED||status==ProjectionOutboxReliabilityStatus.DEAD_LETTER||status==ProjectionOutboxReliabilityStatus.CANCELLED;}
 public boolean claimOwnedBy(String workerId,String tokenHash,OffsetDateTime now){return !terminal()&&workerId!=null&&workerId.equals(claimOwner)&&tokenHash!=null&&tokenHash.equals(claimTokenHash)&&claimUntil!=null&&claimUntil.isAfter(now);}
}
