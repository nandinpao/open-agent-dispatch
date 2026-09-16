package com.opensocket.aievent.database.persistence.issueprojection.po;
import java.time.OffsetDateTime;
public class IssueProjectionStatePo {
 private String tenantId,projectionId,projectionAggregateKey,projectionPurpose,sourceEventId,sourceEventType,sourceAggregateType,sourceAggregateId,taskId,a2aRequestId,handoffSnapshotId,taskIssueLinkId,connectionId,projectMappingId,projectMappingSchemaHash,canonicalDocumentHash,desiredState,observedState,lifecycleStatus,failureClassification,recoveryStrategy,conflictPolicy,desiredPayloadHash,observedPayloadHash,lastAppliedDomainEventId,supersededBy,lastErrorCode,lastErrorMessage,correlationId;
 private int projectMappingVersion,canonicalDocumentSchemaVersion,retryCount,maxAttempts; private long operationSequence,projectionVersion,version; private OffsetDateTime nextRetryAt,createdAt,updatedAt,completedAt;
 public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
 public String getProjectionId(){return projectionId;} public void setProjectionId(String v){projectionId=v;}
 public String getProjectionAggregateKey(){return projectionAggregateKey;} public void setProjectionAggregateKey(String v){projectionAggregateKey=v;}
 public String getProjectionPurpose(){return projectionPurpose;} public void setProjectionPurpose(String v){projectionPurpose=v;}
 public String getSourceEventId(){return sourceEventId;} public void setSourceEventId(String v){sourceEventId=v;}
 public String getSourceEventType(){return sourceEventType;} public void setSourceEventType(String v){sourceEventType=v;}
 public String getSourceAggregateType(){return sourceAggregateType;} public void setSourceAggregateType(String v){sourceAggregateType=v;}
 public String getSourceAggregateId(){return sourceAggregateId;} public void setSourceAggregateId(String v){sourceAggregateId=v;}
 public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
 public String getA2aRequestId(){return a2aRequestId;} public void setA2aRequestId(String v){a2aRequestId=v;}
 public String getHandoffSnapshotId(){return handoffSnapshotId;} public void setHandoffSnapshotId(String v){handoffSnapshotId=v;}
 public String getTaskIssueLinkId(){return taskIssueLinkId;} public void setTaskIssueLinkId(String v){taskIssueLinkId=v;}
 public String getConnectionId(){return connectionId;} public void setConnectionId(String v){connectionId=v;}
 public String getProjectMappingId(){return projectMappingId;} public void setProjectMappingId(String v){projectMappingId=v;}
 public int getProjectMappingVersion(){return projectMappingVersion;} public void setProjectMappingVersion(int v){projectMappingVersion=v;}
 public String getProjectMappingSchemaHash(){return projectMappingSchemaHash;} public void setProjectMappingSchemaHash(String v){projectMappingSchemaHash=v;}
 public int getCanonicalDocumentSchemaVersion(){return canonicalDocumentSchemaVersion;} public void setCanonicalDocumentSchemaVersion(int v){canonicalDocumentSchemaVersion=v;}
 public String getCanonicalDocumentHash(){return canonicalDocumentHash;} public void setCanonicalDocumentHash(String v){canonicalDocumentHash=v;}
 public String getDesiredState(){return desiredState;} public void setDesiredState(String v){desiredState=v;}
 public String getObservedState(){return observedState;} public void setObservedState(String v){observedState=v;}
 public String getLifecycleStatus(){return lifecycleStatus;} public void setLifecycleStatus(String v){lifecycleStatus=v;}
 public String getFailureClassification(){return failureClassification;} public void setFailureClassification(String v){failureClassification=v;}
 public String getRecoveryStrategy(){return recoveryStrategy;} public void setRecoveryStrategy(String v){recoveryStrategy=v;}
 public String getConflictPolicy(){return conflictPolicy;} public void setConflictPolicy(String v){conflictPolicy=v;}
 public String getDesiredPayloadHash(){return desiredPayloadHash;} public void setDesiredPayloadHash(String v){desiredPayloadHash=v;}
 public String getObservedPayloadHash(){return observedPayloadHash;} public void setObservedPayloadHash(String v){observedPayloadHash=v;}
 public String getLastAppliedDomainEventId(){return lastAppliedDomainEventId;} public void setLastAppliedDomainEventId(String v){lastAppliedDomainEventId=v;}
 public long getOperationSequence(){return operationSequence;} public void setOperationSequence(long v){operationSequence=v;}
 public long getProjectionVersion(){return projectionVersion;} public void setProjectionVersion(long v){projectionVersion=v;}
 public String getSupersededBy(){return supersededBy;} public void setSupersededBy(String v){supersededBy=v;}
 public int getRetryCount(){return retryCount;} public void setRetryCount(int v){retryCount=v;}
 public int getMaxAttempts(){return maxAttempts;} public void setMaxAttempts(int v){maxAttempts=v;}
 public OffsetDateTime getNextRetryAt(){return nextRetryAt;} public void setNextRetryAt(OffsetDateTime v){nextRetryAt=v;}
 public String getLastErrorCode(){return lastErrorCode;} public void setLastErrorCode(String v){lastErrorCode=v;}
 public String getLastErrorMessage(){return lastErrorMessage;} public void setLastErrorMessage(String v){lastErrorMessage=v;}
 public long getVersion(){return version;} public void setVersion(long v){version=v;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
 public OffsetDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(OffsetDateTime v){completedAt=v;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
}
