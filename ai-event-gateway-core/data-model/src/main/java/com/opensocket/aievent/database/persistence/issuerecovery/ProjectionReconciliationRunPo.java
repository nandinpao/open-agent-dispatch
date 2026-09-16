package com.opensocket.aievent.database.persistence.issuerecovery;
import java.time.OffsetDateTime;
public class ProjectionReconciliationRunPo {
 private String tenantId;
 private String runId;
 private String connectionId;
 private String projectMappingId;
 private String scopeType;
 private String scopeId;
 private String status;
 private boolean dryRun;
 private int scannedCount;
 private int driftCount;
 private int repairedCount;
 private int failedCount;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String requestedBy;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getRunId(){return runId;} public void setRunId(String value){this.runId=value;}
 public String getConnectionId(){return connectionId;} public void setConnectionId(String value){this.connectionId=value;}
 public String getProjectMappingId(){return projectMappingId;} public void setProjectMappingId(String value){this.projectMappingId=value;}
 public String getScopeType(){return scopeType;} public void setScopeType(String value){this.scopeType=value;}
 public String getScopeId(){return scopeId;} public void setScopeId(String value){this.scopeId=value;}
 public String getStatus(){return status;} public void setStatus(String value){this.status=value;}
 public boolean isDryRun(){return dryRun;} public void setDryRun(boolean value){this.dryRun=value;}
 public int getScannedCount(){return scannedCount;} public void setScannedCount(int value){this.scannedCount=value;}
 public int getDriftCount(){return driftCount;} public void setDriftCount(int value){this.driftCount=value;}
 public int getRepairedCount(){return repairedCount;} public void setRepairedCount(int value){this.repairedCount=value;}
 public int getFailedCount(){return failedCount;} public void setFailedCount(int value){this.failedCount=value;}
 public long getVersion(){return version;} public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getRequestedBy(){return requestedBy;} public void setRequestedBy(String value){this.requestedBy=value;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String value){this.correlationId=value;}
}
