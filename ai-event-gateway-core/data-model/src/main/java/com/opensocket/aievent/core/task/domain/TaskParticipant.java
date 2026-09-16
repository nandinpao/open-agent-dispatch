package com.opensocket.aievent.core.task.domain;

import java.time.OffsetDateTime;

public class TaskParticipant {
    private String tenantId;
    private String participantId;
    private String taskId;
    private TaskParticipantType participantType;
    private String participantRefId;
    private TaskParticipantRole participantRole;
    private TaskParticipantVisibilityLevel visibilityLevel = TaskParticipantVisibilityLevel.STANDARD;
    private TaskParticipantOperationLevel operationLevel = TaskParticipantOperationLevel.READ_ONLY;
    private OffsetDateTime createdAt;
    private String createdBy;
    private long version = 1;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public TaskParticipantType getParticipantType() { return participantType; }
    public void setParticipantType(TaskParticipantType participantType) { this.participantType = participantType; }
    public String getParticipantRefId() { return participantRefId; }
    public void setParticipantRefId(String participantRefId) { this.participantRefId = participantRefId; }
    public TaskParticipantRole getParticipantRole() { return participantRole; }
    public void setParticipantRole(TaskParticipantRole participantRole) { this.participantRole = participantRole; }
    public TaskParticipantVisibilityLevel getVisibilityLevel() { return visibilityLevel; }
    public void setVisibilityLevel(TaskParticipantVisibilityLevel visibilityLevel) { this.visibilityLevel = visibilityLevel == null ? TaskParticipantVisibilityLevel.STANDARD : visibilityLevel; }
    public TaskParticipantOperationLevel getOperationLevel() { return operationLevel; }
    public void setOperationLevel(TaskParticipantOperationLevel operationLevel) { this.operationLevel = operationLevel == null ? TaskParticipantOperationLevel.READ_ONLY : operationLevel; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = Math.max(1L, version); }
}
