package com.opensocket.aievent.core.task;

public class TaskQuery {
    private String incidentId;
    private String tenantId;
    private String siteId;
    private String plantId;
    private TaskType taskType;
    private TaskStatus status;
    private int limit = 100;

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
}
