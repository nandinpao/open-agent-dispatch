package com.opensocket.aievent.database.persistence.issuerelay;
import java.time.OffsetDateTime;

public class RelayEdgePo {
 private String tenantId;
 private String edgeId;
 private String topologyId;
 private String connectionId;
 private String projectMappingId;
 private String providerType;
 private String externalProjectId;
 private String sourceExternalIssueId;
 private String targetExternalIssueId;
 private String edgeType;
 private String relationType;
 private String sourceMarker;
 private String status;
 private int attemptCount;
 private OffsetDateTime nextAttemptAt;
 private String providerObjectId;
 private String lastErrorCode;
 private String lastErrorMessage;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;}
 public void setTenantId(String value){this.tenantId=value;}
 public String getEdgeId(){return edgeId;}
 public void setEdgeId(String value){this.edgeId=value;}
 public String getTopologyId(){return topologyId;}
 public void setTopologyId(String value){this.topologyId=value;}
 public String getConnectionId(){return connectionId;}
 public void setConnectionId(String value){this.connectionId=value;}
 public String getProjectMappingId(){return projectMappingId;}
 public void setProjectMappingId(String value){this.projectMappingId=value;}
 public String getProviderType(){return providerType;}
 public void setProviderType(String value){this.providerType=value;}
 public String getExternalProjectId(){return externalProjectId;}
 public void setExternalProjectId(String value){this.externalProjectId=value;}
 public String getSourceExternalIssueId(){return sourceExternalIssueId;}
 public void setSourceExternalIssueId(String value){this.sourceExternalIssueId=value;}
 public String getTargetExternalIssueId(){return targetExternalIssueId;}
 public void setTargetExternalIssueId(String value){this.targetExternalIssueId=value;}
 public String getEdgeType(){return edgeType;}
 public void setEdgeType(String value){this.edgeType=value;}
 public String getRelationType(){return relationType;}
 public void setRelationType(String value){this.relationType=value;}
 public String getSourceMarker(){return sourceMarker;}
 public void setSourceMarker(String value){this.sourceMarker=value;}
 public String getStatus(){return status;}
 public void setStatus(String value){this.status=value;}
 public int getAttemptCount(){return attemptCount;}
 public void setAttemptCount(int value){this.attemptCount=value;}
 public OffsetDateTime getNextAttemptAt(){return nextAttemptAt;}
 public void setNextAttemptAt(OffsetDateTime value){this.nextAttemptAt=value;}
 public String getProviderObjectId(){return providerObjectId;}
 public void setProviderObjectId(String value){this.providerObjectId=value;}
 public String getLastErrorCode(){return lastErrorCode;}
 public void setLastErrorCode(String value){this.lastErrorCode=value;}
 public String getLastErrorMessage(){return lastErrorMessage;}
 public void setLastErrorMessage(String value){this.lastErrorMessage=value;}
 public long getVersion(){return version;}
 public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;}
 public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;}
 public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;}
 public void setCorrelationId(String value){this.correlationId=value;}
}
