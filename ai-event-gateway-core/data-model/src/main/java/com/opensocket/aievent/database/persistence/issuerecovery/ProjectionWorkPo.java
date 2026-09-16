package com.opensocket.aievent.database.persistence.issuerecovery;
import java.time.OffsetDateTime;
public class ProjectionWorkPo {
 private String tenantId;
 private String workId;
 private String laneId;
 private long laneSequence;
 private long generation;
 private String operationType;
 private String status;
 private String aggregateId;
 private String outboxId;
 private String projectionId;
 private String payloadHash;
 private String coalesceKey;
 private String dependsOnWorkId;
 private String supersededByWorkId;
 private String externalIdempotencyMarker;
 private int attemptCount;
 private int maxAttempts;
 private String claimOwner;
 private String claimTokenHash;
 private OffsetDateTime claimUntil;
 private OffsetDateTime nextAttemptAt;
 private String lastErrorCode;
 private String lastErrorMessage;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getWorkId(){return workId;} public void setWorkId(String value){this.workId=value;}
 public String getLaneId(){return laneId;} public void setLaneId(String value){this.laneId=value;}
 public long getLaneSequence(){return laneSequence;} public void setLaneSequence(long value){this.laneSequence=value;}
 public long getGeneration(){return generation;} public void setGeneration(long value){this.generation=value;}
 public String getOperationType(){return operationType;} public void setOperationType(String value){this.operationType=value;}
 public String getStatus(){return status;} public void setStatus(String value){this.status=value;}
 public String getAggregateId(){return aggregateId;} public void setAggregateId(String value){this.aggregateId=value;}
 public String getOutboxId(){return outboxId;} public void setOutboxId(String value){this.outboxId=value;}
 public String getProjectionId(){return projectionId;} public void setProjectionId(String value){this.projectionId=value;}
 public String getPayloadHash(){return payloadHash;} public void setPayloadHash(String value){this.payloadHash=value;}
 public String getCoalesceKey(){return coalesceKey;} public void setCoalesceKey(String value){this.coalesceKey=value;}
 public String getDependsOnWorkId(){return dependsOnWorkId;} public void setDependsOnWorkId(String value){this.dependsOnWorkId=value;}
 public String getSupersededByWorkId(){return supersededByWorkId;} public void setSupersededByWorkId(String value){this.supersededByWorkId=value;}
 public String getExternalIdempotencyMarker(){return externalIdempotencyMarker;} public void setExternalIdempotencyMarker(String value){this.externalIdempotencyMarker=value;}
 public int getAttemptCount(){return attemptCount;} public void setAttemptCount(int value){this.attemptCount=value;}
 public int getMaxAttempts(){return maxAttempts;} public void setMaxAttempts(int value){this.maxAttempts=value;}
 public String getClaimOwner(){return claimOwner;} public void setClaimOwner(String value){this.claimOwner=value;}
 public String getClaimTokenHash(){return claimTokenHash;} public void setClaimTokenHash(String value){this.claimTokenHash=value;}
 public OffsetDateTime getClaimUntil(){return claimUntil;} public void setClaimUntil(OffsetDateTime value){this.claimUntil=value;}
 public OffsetDateTime getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(OffsetDateTime value){this.nextAttemptAt=value;}
 public String getLastErrorCode(){return lastErrorCode;} public void setLastErrorCode(String value){this.lastErrorCode=value;}
 public String getLastErrorMessage(){return lastErrorMessage;} public void setLastErrorMessage(String value){this.lastErrorMessage=value;}
 public long getVersion(){return version;} public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String value){this.correlationId=value;}
}
