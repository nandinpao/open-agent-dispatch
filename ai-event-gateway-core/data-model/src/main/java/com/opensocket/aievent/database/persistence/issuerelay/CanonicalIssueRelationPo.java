package com.opensocket.aievent.database.persistence.issuerelay;
import java.time.OffsetDateTime;

public class CanonicalIssueRelationPo {
 private String tenantId;
 private String relationId;
 private String relationType;
 private String parentTaskId;
 private String childTaskId;
 private String sourceTaskIssueLinkId;
 private String targetTaskIssueLinkId;
 private String status;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;}
 public void setTenantId(String value){this.tenantId=value;}
 public String getRelationId(){return relationId;}
 public void setRelationId(String value){this.relationId=value;}
 public String getRelationType(){return relationType;}
 public void setRelationType(String value){this.relationType=value;}
 public String getParentTaskId(){return parentTaskId;}
 public void setParentTaskId(String value){this.parentTaskId=value;}
 public String getChildTaskId(){return childTaskId;}
 public void setChildTaskId(String value){this.childTaskId=value;}
 public String getSourceTaskIssueLinkId(){return sourceTaskIssueLinkId;}
 public void setSourceTaskIssueLinkId(String value){this.sourceTaskIssueLinkId=value;}
 public String getTargetTaskIssueLinkId(){return targetTaskIssueLinkId;}
 public void setTargetTaskIssueLinkId(String value){this.targetTaskIssueLinkId=value;}
 public String getStatus(){return status;}
 public void setStatus(String value){this.status=value;}
 public long getVersion(){return version;}
 public void setVersion(long value){this.version=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;}
 public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;}
 public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
 public String getCorrelationId(){return correlationId;}
 public void setCorrelationId(String value){this.correlationId=value;}
}
