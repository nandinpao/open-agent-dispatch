package com.opensocket.aievent.core.action.executor.audit;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdapterExecutorAuditRecord {
    private String auditId;
    private String actionId;
    private String taskId;
    private String incidentId;
    private String adapterType;
    private String actionType;
    private String executorName;
    private String beforeStatus;
    private String afterStatus;
    private String outcome;
    private String message;
    private int attemptCount;
    private OffsetDateTime createdAt;
    private Map<String, Object> payloadSnapshot = new LinkedHashMap<>();

    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getAdapterType() { return adapterType; }
    public void setAdapterType(String adapterType) { this.adapterType = adapterType; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getExecutorName() { return executorName; }
    public void setExecutorName(String executorName) { this.executorName = executorName; }
    public String getBeforeStatus() { return beforeStatus; }
    public void setBeforeStatus(String beforeStatus) { this.beforeStatus = beforeStatus; }
    public String getAfterStatus() { return afterStatus; }
    public void setAfterStatus(String afterStatus) { this.afterStatus = afterStatus; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Map<String, Object> getPayloadSnapshot() { return payloadSnapshot; }
    public void setPayloadSnapshot(Map<String, Object> payloadSnapshot) { this.payloadSnapshot = payloadSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payloadSnapshot); }
}
