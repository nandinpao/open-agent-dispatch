package com.opensocket.aievent.database.persistence.issuechange.po;
import java.time.OffsetDateTime;

public class ExternalCommentSyncPo {
 private String tenantId;
 private String syncId;
 private String connectionId;
 private String projectMappingId;
 private String externalProjectId;
 private String externalIssueId;
 private String taskIssueLinkId;
 private String sourceCommentId;
 private String providerCommentId;
 private String direction;
 private String sourceMarker;
 private String bodyHash;
 private String bodyPreview;
 private String status;
 private int retryCount;
 private String lastErrorCode;
 private String lastErrorMessage;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getSyncId(){return syncId;} public void setSyncId(String value){this.syncId=value;}
 public String getConnectionId(){return connectionId;} public void setConnectionId(String value){this.connectionId=value;}
 public String getProjectMappingId(){return projectMappingId;} public void setProjectMappingId(String value){this.projectMappingId=value;}
 public String getExternalProjectId(){return externalProjectId;} public void setExternalProjectId(String value){this.externalProjectId=value;}
 public String getExternalIssueId(){return externalIssueId;} public void setExternalIssueId(String value){this.externalIssueId=value;}
 public String getTaskIssueLinkId(){return taskIssueLinkId;} public void setTaskIssueLinkId(String value){this.taskIssueLinkId=value;}
 public String getSourceCommentId(){return sourceCommentId;} public void setSourceCommentId(String value){this.sourceCommentId=value;}
 public String getProviderCommentId(){return providerCommentId;} public void setProviderCommentId(String value){this.providerCommentId=value;}
 public String getDirection(){return direction;} public void setDirection(String value){this.direction=value;}
 public String getSourceMarker(){return sourceMarker;} public void setSourceMarker(String value){this.sourceMarker=value;}
 public String getBodyHash(){return bodyHash;} public void setBodyHash(String value){this.bodyHash=value;}
 public String getBodyPreview(){return bodyPreview;} public void setBodyPreview(String value){this.bodyPreview=value;}
 public String getStatus(){return status;} public void setStatus(String value){this.status=value;}
 public int getRetryCount(){return retryCount;} public void setRetryCount(int value){this.retryCount=value;}
 public String getLastErrorCode(){return lastErrorCode;} public void setLastErrorCode(String value){this.lastErrorCode=value;}
 public String getLastErrorMessage(){return lastErrorMessage;} public void setLastErrorMessage(String value){this.lastErrorMessage=value;}
 public long getVersion(){return version;} public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String value){this.correlationId=value;}
}
