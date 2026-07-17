package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.util.List;

/** Conditional execution-state update for tasks. */
public class TaskExecutionStateTransition {
    private String taskId;
    private List<TaskStatus> allowedCurrentStatuses = List.of();
    private TaskStatus newStatus;
    private OffsetDateTime timeoutAt;
    private OffsetDateTime terminalAt;
    private OffsetDateTime updatedAt;
    private String lifecycleReason;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public List<TaskStatus> getAllowedCurrentStatuses() { return allowedCurrentStatuses; }
    public void setAllowedCurrentStatuses(List<TaskStatus> allowedCurrentStatuses) { this.allowedCurrentStatuses = allowedCurrentStatuses == null ? List.of() : allowedCurrentStatuses; }
    public TaskStatus getNewStatus() { return newStatus; }
    public void setNewStatus(TaskStatus newStatus) { this.newStatus = newStatus; }
    public OffsetDateTime getTimeoutAt() { return timeoutAt; }
    public void setTimeoutAt(OffsetDateTime timeoutAt) { this.timeoutAt = timeoutAt; }
    public OffsetDateTime getTerminalAt() { return terminalAt; }
    public void setTerminalAt(OffsetDateTime terminalAt) { this.terminalAt = terminalAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getLifecycleReason() { return lifecycleReason; }
    public void setLifecycleReason(String lifecycleReason) { this.lifecycleReason = lifecycleReason; }

    public List<String> allowedCurrentStatusNames() {
        return allowedCurrentStatuses.stream().map(Enum::name).toList();
    }

    public String newStatusName() {
        return newStatus == null ? null : newStatus.name();
    }
}
