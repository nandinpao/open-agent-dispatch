package com.opensocket.aievent.core.issuetracking.recovery;
import java.time.OffsetDateTime;
public record OrderedProjectionWork(String tenantId,String workId,String laneId,long laneSequence,long generation,ProjectionOperationType operationType,ProjectionWorkStatus status,String aggregateId,String outboxId,String projectionId,String payloadHash,String coalesceKey,String dependsOnWorkId,String supersededByWorkId,String externalIdempotencyMarker,int attemptCount,int maxAttempts,String claimOwner,String claimTokenHash,OffsetDateTime claimUntil,OffsetDateTime nextAttemptAt,String lastErrorCode,String lastErrorMessage,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt,String correlationId){
 public OrderedProjectionWork { if(blank(tenantId)||blank(workId)||blank(laneId)||laneSequence<1||generation<1||operationType==null||status==null||blank(aggregateId)||blank(payloadHash)||blank(externalIdempotencyMarker)||maxAttempts<1||attemptCount<0||version<1) throw new IllegalArgumentException("Invalid ordered projection work."); }
 private static boolean blank(String v){return v==null||v.isBlank();}
 public boolean terminal(){return status==ProjectionWorkStatus.ACKNOWLEDGED||status==ProjectionWorkStatus.DEAD_LETTER||status==ProjectionWorkStatus.SUPERSEDED||status==ProjectionWorkStatus.CANCELLED;}
}
