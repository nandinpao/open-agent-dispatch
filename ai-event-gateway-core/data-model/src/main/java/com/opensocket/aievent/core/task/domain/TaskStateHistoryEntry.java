package com.opensocket.aievent.core.task.domain;

import java.time.OffsetDateTime;
import com.opensocket.aievent.core.task.TaskStatus;

public class TaskStateHistoryEntry {
    private String tenantId;
    private String historyId;
    private String taskId;
    private TaskStatus fromStatus;
    private TaskStatus toStatus;
    private String reasonCode;
    private String reason;
    private TaskActorType actorType = TaskActorType.SYSTEM;
    private String actorId;
    private String correlationId;
    private OffsetDateTime transitionAt;
    private long taskVersion;
    private String idempotencyKey;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public TaskStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(TaskStatus fromStatus) { this.fromStatus = fromStatus; }
    public TaskStatus getToStatus() { return toStatus; }
    public void setToStatus(TaskStatus toStatus) { this.toStatus = toStatus; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public TaskActorType getActorType() { return actorType; }
    public void setActorType(TaskActorType actorType) { this.actorType = actorType == null ? TaskActorType.SYSTEM : actorType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public OffsetDateTime getTransitionAt() { return transitionAt; }
    public void setTransitionAt(OffsetDateTime transitionAt) { this.transitionAt = transitionAt; }
    public long getTaskVersion() { return taskVersion; }
    public void setTaskVersion(long taskVersion) { this.taskVersion = taskVersion; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
