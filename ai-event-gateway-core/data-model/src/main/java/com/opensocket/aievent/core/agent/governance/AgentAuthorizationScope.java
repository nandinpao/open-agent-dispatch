package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;

public class AgentAuthorizationScope {
    private String scopeId;
    private String agentId;
    private String tenantId;
    private String systemCode;
    private String siteCode;
    private String eventType;
    private String taskType;
    private String dataClassificationLimit;
    private boolean enabled = true;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getDataClassificationLimit() { return dataClassificationLimit; }
    public void setDataClassificationLimit(String dataClassificationLimit) { this.dataClassificationLimit = dataClassificationLimit; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
