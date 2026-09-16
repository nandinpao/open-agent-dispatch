package com.opensocket.aievent.database.persistence.issuerelay;
import java.time.OffsetDateTime;

public class RelayTopologyPo {
 private String tenantId;
 private String topologyId;
 private String canonicalRelationId;
 private String topologyType;
 private String status;
 private int edgeCount;
 private int syncedEdgeCount;
 private int failedEdgeCount;
 private int decisionEdgeCount;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;}
 public void setTenantId(String value){this.tenantId=value;}
 public String getTopologyId(){return topologyId;}
 public void setTopologyId(String value){this.topologyId=value;}
 public String getCanonicalRelationId(){return canonicalRelationId;}
 public void setCanonicalRelationId(String value){this.canonicalRelationId=value;}
 public String getTopologyType(){return topologyType;}
 public void setTopologyType(String value){this.topologyType=value;}
 public String getStatus(){return status;}
 public void setStatus(String value){this.status=value;}
 public int getEdgeCount(){return edgeCount;}
 public void setEdgeCount(int value){this.edgeCount=value;}
 public int getSyncedEdgeCount(){return syncedEdgeCount;}
 public void setSyncedEdgeCount(int value){this.syncedEdgeCount=value;}
 public int getFailedEdgeCount(){return failedEdgeCount;}
 public void setFailedEdgeCount(int value){this.failedEdgeCount=value;}
 public int getDecisionEdgeCount(){return decisionEdgeCount;}
 public void setDecisionEdgeCount(int value){this.decisionEdgeCount=value;}
 public long getVersion(){return version;}
 public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;}
 public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;}
 public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;}
 public void setCorrelationId(String value){this.correlationId=value;}
}
