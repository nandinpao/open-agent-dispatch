package com.opensocket.aievent.database.persistence.task.po;

import java.time.OffsetDateTime;

public class TaskRelationshipPo {
    private String tenantId;
    private String relationshipId;
    private String fromTaskId;
    private String toTaskId;
    private String relationshipType;
    private String direction;
    private String reasonCode;
    private String description;
    private String referenceVisibility;
    private String idempotencyKey;
    private String createdByType;
    private String createdById;
    private OffsetDateTime createdAt;
    private long version;
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getRelationshipId(){return relationshipId;} public void setRelationshipId(String v){relationshipId=v;}
    public String getFromTaskId(){return fromTaskId;} public void setFromTaskId(String v){fromTaskId=v;}
    public String getToTaskId(){return toTaskId;} public void setToTaskId(String v){toTaskId=v;}
    public String getRelationshipType(){return relationshipType;} public void setRelationshipType(String v){relationshipType=v;}
    public String getDirection(){return direction;} public void setDirection(String v){direction=v;}
    public String getReasonCode(){return reasonCode;} public void setReasonCode(String v){reasonCode=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getReferenceVisibility(){return referenceVisibility;} public void setReferenceVisibility(String v){referenceVisibility=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public String getCreatedByType(){return createdByType;} public void setCreatedByType(String v){createdByType=v;}
    public String getCreatedById(){return createdById;} public void setCreatedById(String v){createdById=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public long getVersion(){return version;} public void setVersion(long v){version=v;}
}
