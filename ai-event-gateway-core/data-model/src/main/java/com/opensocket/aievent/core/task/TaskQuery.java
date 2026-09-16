package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;

public class TaskQuery {
    private String incidentId;
    private String tenantId;
    private String siteId;
    private String plantId;
    private TaskType taskType;
    private TaskStatus status;
    private int limit = 100;
    private OffsetDateTime beforeCreatedAt;
    private String beforeTaskId;

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public int getLimit() { return Math.max(1, Math.min(limit, 1000)); }
    public void setLimit(int limit) { this.limit = limit; }
    public OffsetDateTime getBeforeCreatedAt() { return beforeCreatedAt; }
    public void setBeforeCreatedAt(OffsetDateTime beforeCreatedAt) { this.beforeCreatedAt = beforeCreatedAt; }
    public String getBeforeTaskId() { return beforeTaskId; }
    public void setBeforeTaskId(String beforeTaskId) { this.beforeTaskId = beforeTaskId; }
    public boolean hasCursor() { return beforeCreatedAt != null && beforeTaskId != null && !beforeTaskId.isBlank(); }
}
