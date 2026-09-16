package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
/** Secret-free immutable certification evidence. */
public record ResourceAccessCertificationEvidence(
 String evidenceId,String tenantId,ResourceAccessCertificationType evidenceType,ResourceAccessCertificationStatus status,
 String commandName,String artifactRef,String artifactSha256,String summary,Instant startedAt,Instant completedAt,String actorId,String correlationId) {
 public ResourceAccessCertificationEvidence {
  evidenceId=req(evidenceId,"evidenceId");tenantId=req(tenantId,"tenantId");if(evidenceType==null||status==null)throw new IllegalArgumentException("evidence type and status are required");
  commandName=req(commandName,"commandName");artifactRef=clean(artifactRef);artifactSha256=clean(artifactSha256);summary=clean(summary);
  if(startedAt==null||completedAt==null||completedAt.isBefore(startedAt))throw new IllegalArgumentException("valid evidence timestamps are required");
  actorId=req(actorId,"actorId");correlationId=req(correlationId,"correlationId");
 }
 private static String clean(String v){return v==null?"":v.trim();}
 private static String req(String v,String f){String x=clean(v);if(x.isBlank())throw new IllegalArgumentException(f+" is required");return x;}
}
