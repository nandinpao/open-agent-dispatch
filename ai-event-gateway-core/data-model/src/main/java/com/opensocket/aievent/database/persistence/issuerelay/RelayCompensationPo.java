package com.opensocket.aievent.database.persistence.issuerelay;
import java.time.OffsetDateTime;

public class RelayCompensationPo {
 private String tenantId;
 private String compensationId;
 private String topologyId;
 private String canonicalRelationId;
 private String reasonCode;
 private String requestedBy;
 private String status;
 private int totalEdges;
 private int compensatedEdges;
 private int failedEdges;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;}
 public void setTenantId(String value){this.tenantId=value;}
 public String getCompensationId(){return compensationId;}
 public void setCompensationId(String value){this.compensationId=value;}
 public String getTopologyId(){return topologyId;}
 public void setTopologyId(String value){this.topologyId=value;}
 public String getCanonicalRelationId(){return canonicalRelationId;}
 public void setCanonicalRelationId(String value){this.canonicalRelationId=value;}
 public String getReasonCode(){return reasonCode;}
 public void setReasonCode(String value){this.reasonCode=value;}
 public String getRequestedBy(){return requestedBy;}
 public void setRequestedBy(String value){this.requestedBy=value;}
 public String getStatus(){return status;}
 public void setStatus(String value){this.status=value;}
 public int getTotalEdges(){return totalEdges;}
 public void setTotalEdges(int value){this.totalEdges=value;}
 public int getCompensatedEdges(){return compensatedEdges;}
 public void setCompensatedEdges(int value){this.compensatedEdges=value;}
 public int getFailedEdges(){return failedEdges;}
 public void setFailedEdges(int value){this.failedEdges=value;}
 public long getVersion(){return version;}
 public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;}
 public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;}
 public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;}
 public void setCorrelationId(String value){this.correlationId=value;}
}
