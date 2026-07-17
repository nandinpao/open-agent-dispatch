package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.util.List;

public class TaskRecord {
    private String taskId;
    private String incidentId;
    private String sourceEventId;
    private String sourceSystem;
    private String eventStage;
    private String originSourceSystem;
    private String targetSystem;
    private TaskType taskType;
    /**
     * Business task contract code resolved from sourceSystem/objectType/eventType/errorCode.
     * Kept separate from taskType enum so tenant-defined business task codes
     * can route through the strict dispatch contract while legacy lifecycle code remains enum-compatible.
     */
    private String taskTypeCode;
    private TaskStatus status;
    private TaskPriority priority;
    private String tenantId;
    private String siteId;
    private String plantId;
    private String objectType;
    private String objectId;
    private String eventType;
    private String errorCode;
    private String requestedSkill;
    private String handoffMode;
    private String correlationId;
    private String parentTaskId;
    private String matchedFlowId;
    private String matchedRuleId;
    private String assignedPoolId;
    private String targetPoolId;
    private String classificationStatus = "CLASSIFIED";
    private String classificationResultJson = "{}";
    private String routingPath;
    private String routingPolicy;
    private List<String> requiredCapabilities = List.of();
    private String createdReason;
    private long occurrenceCountAtCreation;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime timeoutAt;
    private OffsetDateTime terminalAt;
    private int reassignmentCount;
    private OffsetDateTime nextDispatchAttemptAt;
    private int dispatchAttemptCount;
    private String dispatchRetryReason;
    private String dispatchRecoveryClaimedBy;
    private OffsetDateTime dispatchRecoveryClaimUntil;
    private String lifecycleReason;
    /** Stable key that an external executor must use to deduplicate effectful execution. */
    private String externalExecutionKey;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getOriginSourceSystem() { return originSourceSystem; }
    public void setOriginSourceSystem(String originSourceSystem) { this.originSourceSystem = originSourceSystem; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }
    public String getTaskTypeCode() { return taskTypeCode; }
    public void setTaskTypeCode(String taskTypeCode) { this.taskTypeCode = taskTypeCode; }
    public String getEffectiveTaskTypeCode() {
        if (taskTypeCode != null && !taskTypeCode.isBlank()) {
            return taskTypeCode;
        }
        return taskType == null ? null : taskType.name();
    }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getHandoffMode() { return handoffMode; }
    public void setHandoffMode(String handoffMode) { this.handoffMode = handoffMode; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getAssignedPoolId() { return assignedPoolId; }
    public void setAssignedPoolId(String assignedPoolId) { this.assignedPoolId = assignedPoolId; }
    public String getTargetPoolId() { return targetPoolId; }
    public void setTargetPoolId(String targetPoolId) { this.targetPoolId = targetPoolId; }
    public String getClassificationStatus() { return classificationStatus; }
    public void setClassificationStatus(String classificationStatus) { this.classificationStatus = classificationStatus == null || classificationStatus.isBlank() ? "CLASSIFIED" : classificationStatus; }
    public String getClassificationResultJson() { return classificationResultJson; }
    public void setClassificationResultJson(String classificationResultJson) { this.classificationResultJson = classificationResultJson == null || classificationResultJson.isBlank() ? "{}" : classificationResultJson; }
    public String getRoutingPath() { return routingPath; }
    public void setRoutingPath(String routingPath) { this.routingPath = routingPath; }
    public String getRoutingPolicy() { return routingPolicy; }
    public void setRoutingPolicy(String routingPolicy) { this.routingPolicy = routingPolicy; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities); }
    public String getCreatedReason() { return createdReason; }
    public void setCreatedReason(String createdReason) { this.createdReason = createdReason; }
    public long getOccurrenceCountAtCreation() { return occurrenceCountAtCreation; }
    public void setOccurrenceCountAtCreation(long occurrenceCountAtCreation) { this.occurrenceCountAtCreation = occurrenceCountAtCreation; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getTimeoutAt() { return timeoutAt; }
    public void setTimeoutAt(OffsetDateTime timeoutAt) { this.timeoutAt = timeoutAt; }
    public OffsetDateTime getTerminalAt() { return terminalAt; }
    public void setTerminalAt(OffsetDateTime terminalAt) { this.terminalAt = terminalAt; }
    public int getReassignmentCount() { return reassignmentCount; }
    public void setReassignmentCount(int reassignmentCount) { this.reassignmentCount = Math.max(0, reassignmentCount); }
    public OffsetDateTime getNextDispatchAttemptAt() { return nextDispatchAttemptAt; }
    public void setNextDispatchAttemptAt(OffsetDateTime nextDispatchAttemptAt) { this.nextDispatchAttemptAt = nextDispatchAttemptAt; }
    public int getDispatchAttemptCount() { return dispatchAttemptCount; }
    public void setDispatchAttemptCount(int dispatchAttemptCount) { this.dispatchAttemptCount = Math.max(0, dispatchAttemptCount); }
    public String getDispatchRetryReason() { return dispatchRetryReason; }
    public void setDispatchRetryReason(String dispatchRetryReason) { this.dispatchRetryReason = dispatchRetryReason; }
    public String getDispatchRecoveryClaimedBy() { return dispatchRecoveryClaimedBy; }
    public void setDispatchRecoveryClaimedBy(String dispatchRecoveryClaimedBy) { this.dispatchRecoveryClaimedBy = dispatchRecoveryClaimedBy; }
    public OffsetDateTime getDispatchRecoveryClaimUntil() { return dispatchRecoveryClaimUntil; }
    public void setDispatchRecoveryClaimUntil(OffsetDateTime dispatchRecoveryClaimUntil) { this.dispatchRecoveryClaimUntil = dispatchRecoveryClaimUntil; }
    public String getLifecycleReason() { return lifecycleReason; }
    public void setLifecycleReason(String lifecycleReason) { this.lifecycleReason = lifecycleReason; }
    public String getExternalExecutionKey() { return externalExecutionKey; }
    public void setExternalExecutionKey(String externalExecutionKey) { this.externalExecutionKey = externalExecutionKey; }
}
