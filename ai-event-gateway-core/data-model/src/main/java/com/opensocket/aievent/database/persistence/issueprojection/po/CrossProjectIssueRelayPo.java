package com.opensocket.aievent.database.persistence.issueprojection.po;
import java.time.OffsetDateTime;
public class CrossProjectIssueRelayPo {
 private String tenantId;
 private String relayId;
 private String a2aRequestId;
 private String rootTaskId;
 private String sourceTaskId;
 private String targetTaskId;
 private String sourceMappingId;
 private String targetMappingId;
 private String sourceSnapshotId;
 private String resultSnapshotId;
 private String sourceIssueLinkId;
 private String targetIssueLinkId;
 private String strategy;
 private String relayState;
 private Boolean nativeRelationSupported;
 private String sourceBacklinkStatus;
 private String targetBacklinkStatus;
 private int retryCount;
 private OffsetDateTime nextRetryAt;
 private String lastErrorCode;
 private String lastErrorMessage;
 private String idempotencyKey;
 private String correlationId;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 public String getTenantId(){return tenantId;} public void setTenantId(String tenantId){this.tenantId=tenantId;}
 public String getRelayId(){return relayId;} public void setRelayId(String relayId){this.relayId=relayId;}
 public String getA2aRequestId(){return a2aRequestId;} public void setA2aRequestId(String a2aRequestId){this.a2aRequestId=a2aRequestId;}
 public String getRootTaskId(){return rootTaskId;} public void setRootTaskId(String rootTaskId){this.rootTaskId=rootTaskId;}
 public String getSourceTaskId(){return sourceTaskId;} public void setSourceTaskId(String sourceTaskId){this.sourceTaskId=sourceTaskId;}
 public String getTargetTaskId(){return targetTaskId;} public void setTargetTaskId(String targetTaskId){this.targetTaskId=targetTaskId;}
 public String getSourceMappingId(){return sourceMappingId;} public void setSourceMappingId(String sourceMappingId){this.sourceMappingId=sourceMappingId;}
 public String getTargetMappingId(){return targetMappingId;} public void setTargetMappingId(String targetMappingId){this.targetMappingId=targetMappingId;}
 public String getSourceSnapshotId(){return sourceSnapshotId;} public void setSourceSnapshotId(String sourceSnapshotId){this.sourceSnapshotId=sourceSnapshotId;}
 public String getResultSnapshotId(){return resultSnapshotId;} public void setResultSnapshotId(String resultSnapshotId){this.resultSnapshotId=resultSnapshotId;}
 public String getSourceIssueLinkId(){return sourceIssueLinkId;} public void setSourceIssueLinkId(String sourceIssueLinkId){this.sourceIssueLinkId=sourceIssueLinkId;}
 public String getTargetIssueLinkId(){return targetIssueLinkId;} public void setTargetIssueLinkId(String targetIssueLinkId){this.targetIssueLinkId=targetIssueLinkId;}
 public String getStrategy(){return strategy;} public void setStrategy(String strategy){this.strategy=strategy;}
 public String getRelayState(){return relayState;} public void setRelayState(String relayState){this.relayState=relayState;}
 public Boolean getNativeRelationSupported(){return nativeRelationSupported;} public void setNativeRelationSupported(Boolean nativeRelationSupported){this.nativeRelationSupported=nativeRelationSupported;}
 public String getSourceBacklinkStatus(){return sourceBacklinkStatus;} public void setSourceBacklinkStatus(String sourceBacklinkStatus){this.sourceBacklinkStatus=sourceBacklinkStatus;}
 public String getTargetBacklinkStatus(){return targetBacklinkStatus;} public void setTargetBacklinkStatus(String targetBacklinkStatus){this.targetBacklinkStatus=targetBacklinkStatus;}
 public int getRetryCount(){return retryCount;} public void setRetryCount(int retryCount){this.retryCount=retryCount;}
 public OffsetDateTime getNextRetryAt(){return nextRetryAt;} public void setNextRetryAt(OffsetDateTime nextRetryAt){this.nextRetryAt=nextRetryAt;}
 public String getLastErrorCode(){return lastErrorCode;} public void setLastErrorCode(String lastErrorCode){this.lastErrorCode=lastErrorCode;}
 public String getLastErrorMessage(){return lastErrorMessage;} public void setLastErrorMessage(String lastErrorMessage){this.lastErrorMessage=lastErrorMessage;}
 public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String idempotencyKey){this.idempotencyKey=idempotencyKey;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String correlationId){this.correlationId=correlationId;}
 public long getVersion(){return version;} public void setVersion(long version){this.version=version;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime createdAt){this.createdAt=createdAt;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime updatedAt){this.updatedAt=updatedAt;}
}
