package com.opensocket.aievent.core.issuetracking.recovery;
import java.time.OffsetDateTime;
public record ProjectionRecoveryCase(String tenantId,String caseId,ProjectionRecoveryCaseType caseType,ProjectionRecoveryCaseStatus status,String laneId,String workId,String projectionId,String connectionId,String projectMappingId,String externalIdempotencyMarker,String externalIssueId,String expectedHash,String observedHash,String reasonCode,String safeSummary,int attemptCount,OffsetDateTime nextAttemptAt,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt,String correlationId){
 public ProjectionRecoveryCase { if(blank(tenantId)||blank(caseId)||caseType==null||status==null||blank(connectionId)||blank(reasonCode)||version<1||attemptCount<0) throw new IllegalArgumentException("Invalid projection recovery case."); }
 private static boolean blank(String v){return v==null||v.isBlank();}
}
