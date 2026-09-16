package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
import java.util.List;
/** Immutable release assessment. An assessment is evidence, never an authorization token. */
public record ResourceAccessReleaseAssessment(
 String assessmentId,String tenantId,ResourceAccessEnforcementMode fromMode,ResourceAccessEnforcementMode targetMode,
 ResourceAccessReleaseGateStatus status,long sampleCount,long mismatchCount,long criticalMismatchCount,
 long legacyUnavailableCount,int mismatchRateBps,int legacyUnavailableRateBps,
 List<String> blockingReasons,Instant windowStartedAt,Instant windowEndedAt,Instant assessedAt,String assessedBy,String correlationId) {
 public ResourceAccessReleaseAssessment {
  assessmentId=req(assessmentId,"assessmentId");tenantId=req(tenantId,"tenantId");
  if(fromMode==null||targetMode==null||status==null) throw new IllegalArgumentException("mode and status are required");
  if(sampleCount<0||mismatchCount<0||criticalMismatchCount<0||legacyUnavailableCount<0) throw new IllegalArgumentException("counts must be non-negative");
  blockingReasons=blockingReasons==null?List.of():List.copyOf(blockingReasons);
  if(windowStartedAt==null||windowEndedAt==null||windowEndedAt.isBefore(windowStartedAt)||assessedAt==null) throw new IllegalArgumentException("valid timestamps are required");
  assessedBy=req(assessedBy,"assessedBy");correlationId=req(correlationId,"correlationId");
 }
 public boolean executableTransitionEvidence(){return status==ResourceAccessReleaseGateStatus.PASSED;}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
