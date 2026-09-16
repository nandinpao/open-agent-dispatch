package com.opensocket.aievent.core.task.domain;

import java.time.OffsetDateTime;

public class TaskRelationship {
    private String tenantId;
    private String relationshipId;
    private String fromTaskId;
    private String toTaskId;
    private TaskRelationshipType relationshipType;
    private TaskRelationshipDirection direction = TaskRelationshipDirection.DIRECTED;
    private String reasonCode;
    private String description;
    private TaskReferenceVisibility referenceVisibility = TaskReferenceVisibility.VISIBLE;
    private String idempotencyKey;
    private TaskActorType createdByType = TaskActorType.SYSTEM;
    private String createdById;
    private OffsetDateTime createdAt;
    private long version = 1;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getRelationshipId() { return relationshipId; }
    public void setRelationshipId(String relationshipId) { this.relationshipId = relationshipId; }
    public String getFromTaskId() { return fromTaskId; }
    public void setFromTaskId(String fromTaskId) { this.fromTaskId = fromTaskId; }
    public String getToTaskId() { return toTaskId; }
    public void setToTaskId(String toTaskId) { this.toTaskId = toTaskId; }
    public TaskRelationshipType getRelationshipType() { return relationshipType; }
    public void setRelationshipType(TaskRelationshipType relationshipType) { this.relationshipType = relationshipType; }
    public TaskRelationshipDirection getDirection() { return direction; }
    public void setDirection(TaskRelationshipDirection direction) { this.direction = direction == null ? TaskRelationshipDirection.DIRECTED : direction; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TaskReferenceVisibility getReferenceVisibility() { return referenceVisibility; }
    public void setReferenceVisibility(TaskReferenceVisibility referenceVisibility) { this.referenceVisibility = referenceVisibility == null ? TaskReferenceVisibility.VISIBLE : referenceVisibility; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public TaskActorType getCreatedByType() { return createdByType; }
    public void setCreatedByType(TaskActorType createdByType) { this.createdByType = createdByType == null ? TaskActorType.SYSTEM : createdByType; }
    public String getCreatedById() { return createdById; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = Math.max(1L, version); }
}
