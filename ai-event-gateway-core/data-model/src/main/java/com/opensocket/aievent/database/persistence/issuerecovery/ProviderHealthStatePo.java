package com.opensocket.aievent.database.persistence.issuerecovery;
import java.time.OffsetDateTime;
public class ProviderHealthStatePo {
 private String tenantId;
 private String healthId;
 private String connectionId;
 private String projectMappingId;
 private String status;
 private int consecutiveFailures;
 private int successCount;
 private int failureCount;
 private int queueDepth;
 private int maxQueueDepth;
 private OffsetDateTime openedAt;
 private OffsetDateTime retryAfterAt;
 private String lastFailureCode;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getHealthId(){return healthId;} public void setHealthId(String value){this.healthId=value;}
 public String getConnectionId(){return connectionId;} public void setConnectionId(String value){this.connectionId=value;}
 public String getProjectMappingId(){return projectMappingId;} public void setProjectMappingId(String value){this.projectMappingId=value;}
 public String getStatus(){return status;} public void setStatus(String value){this.status=value;}
 public int getConsecutiveFailures(){return consecutiveFailures;} public void setConsecutiveFailures(int value){this.consecutiveFailures=value;}
 public int getSuccessCount(){return successCount;} public void setSuccessCount(int value){this.successCount=value;}
 public int getFailureCount(){return failureCount;} public void setFailureCount(int value){this.failureCount=value;}
 public int getQueueDepth(){return queueDepth;} public void setQueueDepth(int value){this.queueDepth=value;}
 public int getMaxQueueDepth(){return maxQueueDepth;} public void setMaxQueueDepth(int value){this.maxQueueDepth=value;}
 public OffsetDateTime getOpenedAt(){return openedAt;} public void setOpenedAt(OffsetDateTime value){this.openedAt=value;}
 public OffsetDateTime getRetryAfterAt(){return retryAfterAt;} public void setRetryAfterAt(OffsetDateTime value){this.retryAfterAt=value;}
 public String getLastFailureCode(){return lastFailureCode;} public void setLastFailureCode(String value){this.lastFailureCode=value;}
 public long getVersion(){return version;} public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String value){this.correlationId=value;}
}
