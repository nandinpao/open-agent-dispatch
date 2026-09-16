package com.opensocket.aievent.database.persistence.issuerecovery;
import java.time.OffsetDateTime;
public class ProjectionLanePo {
 private String tenantId;
 private String laneId;
 private String aggregateKey;
 private String laneType;
 private String connectionId;
 private String projectMappingId;
 private String status;
 private long nextSequence;
 private long activeGeneration;
 private int maxQueueDepth;
 private int queuedCount;
 private int inFlightCount;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getLaneId(){return laneId;} public void setLaneId(String value){this.laneId=value;}
 public String getAggregateKey(){return aggregateKey;} public void setAggregateKey(String value){this.aggregateKey=value;}
 public String getLaneType(){return laneType;} public void setLaneType(String value){this.laneType=value;}
 public String getConnectionId(){return connectionId;} public void setConnectionId(String value){this.connectionId=value;}
 public String getProjectMappingId(){return projectMappingId;} public void setProjectMappingId(String value){this.projectMappingId=value;}
 public String getStatus(){return status;} public void setStatus(String value){this.status=value;}
 public long getNextSequence(){return nextSequence;} public void setNextSequence(long value){this.nextSequence=value;}
 public long getActiveGeneration(){return activeGeneration;} public void setActiveGeneration(long value){this.activeGeneration=value;}
 public int getMaxQueueDepth(){return maxQueueDepth;} public void setMaxQueueDepth(int value){this.maxQueueDepth=value;}
 public int getQueuedCount(){return queuedCount;} public void setQueuedCount(int value){this.queuedCount=value;}
 public int getInFlightCount(){return inFlightCount;} public void setInFlightCount(int value){this.inFlightCount=value;}
 public long getVersion(){return version;} public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String value){this.correlationId=value;}
}
