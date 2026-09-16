package com.opensocket.aievent.core.issuetracking.recovery;
import java.time.OffsetDateTime;
public record ProjectionReconciliationRun(String tenantId,String runId,String connectionId,String projectMappingId,String scopeType,String scopeId,ReconciliationRunStatus status,boolean dryRun,int scannedCount,int driftCount,int repairedCount,int failedCount,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt,String requestedBy,String correlationId){
 public ProjectionReconciliationRun { if(blank(tenantId)||blank(runId)||blank(connectionId)||blank(scopeType)||status==null||scannedCount<0||driftCount<0||repairedCount<0||failedCount<0||version<1) throw new IllegalArgumentException("Invalid reconciliation run."); }
 private static boolean blank(String v){return v==null||v.isBlank();}
}
