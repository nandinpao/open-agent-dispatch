package com.opensocket.aievent.database.persistence.issuerecovery;
import java.time.OffsetDateTime;
public class ProjectionRecoveryCasePo {
 private String tenantId;
 private String caseId;
 private String caseType;
 private String status;
 private String laneId;
 private String workId;
 private String projectionId;
 private String connectionId;
 private String projectMappingId;
 private String externalIdempotencyMarker;
 private String externalIssueId;
 private String expectedHash;
 private String observedHash;
 private String reasonCode;
 private String safeSummary;
 private int attemptCount;
 private OffsetDateTime nextAttemptAt;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getCaseId(){return caseId;} public void setCaseId(String value){this.caseId=value;}
 public String getCaseType(){return caseType;} public void setCaseType(String value){this.caseType=value;}
 public String getStatus(){return status;} public void setStatus(String value){this.status=value;}
 public String getLaneId(){return laneId;} public void setLaneId(String value){this.laneId=value;}
 public String getWorkId(){return workId;} public void setWorkId(String value){this.workId=value;}
 public String getProjectionId(){return projectionId;} public void setProjectionId(String value){this.projectionId=value;}
 public String getConnectionId(){return connectionId;} public void setConnectionId(String value){this.connectionId=value;}
 public String getProjectMappingId(){return projectMappingId;} public void setProjectMappingId(String value){this.projectMappingId=value;}
 public String getExternalIdempotencyMarker(){return externalIdempotencyMarker;} public void setExternalIdempotencyMarker(String value){this.externalIdempotencyMarker=value;}
 public String getExternalIssueId(){return externalIssueId;} public void setExternalIssueId(String value){this.externalIssueId=value;}
 public String getExpectedHash(){return expectedHash;} public void setExpectedHash(String value){this.expectedHash=value;}
 public String getObservedHash(){return observedHash;} public void setObservedHash(String value){this.observedHash=value;}
 public String getReasonCode(){return reasonCode;} public void setReasonCode(String value){this.reasonCode=value;}
 public String getSafeSummary(){return safeSummary;} public void setSafeSummary(String value){this.safeSummary=value;}
 public int getAttemptCount(){return attemptCount;} public void setAttemptCount(int value){this.attemptCount=value;}
 public OffsetDateTime getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(OffsetDateTime value){this.nextAttemptAt=value;}
 public long getVersion(){return version;} public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String value){this.correlationId=value;}
}
