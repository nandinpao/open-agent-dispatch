package com.opensocket.aievent.core.action;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdapterAction {
    private String actionId;
    private String idempotencyKey;
    private String incidentId;
    private String taskId;
    private String dispatchRequestId;
    private String assignmentId;
    private String agentId;
    private String adapterName;
    private AdapterType adapterType;
    private AdapterActionType actionType;
    private AdapterActionStatus status;
    private String reason;
    private String requestHash;
    private String responseRef;
    private Map<String, Object> payload = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime executingAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime failedAt;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime retryWaitingAt;
    private OffsetDateTime executorUnavailableAt;
    private String claimedBy;
    private OffsetDateTime claimedAt;
    private OffsetDateTime leaseExpiresAt;
    private OffsetDateTime workerHeartbeatAt;
    private int attemptCount;
    private int maxAttempts;
    private String executorName;
    private String lastError;

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAdapterName() { return adapterName; }
    public void setAdapterName(String adapterName) { this.adapterName = adapterName; }
    public AdapterType getAdapterType() { return adapterType; }
    public void setAdapterType(AdapterType adapterType) { this.adapterType = adapterType; }
    public AdapterActionType getActionType() { return actionType; }
    public void setActionType(AdapterActionType actionType) { this.actionType = actionType; }
    public AdapterActionStatus getStatus() { return status; }
    public void setStatus(AdapterActionStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getResponseRef() { return responseRef; }
    public void setResponseRef(String responseRef) { this.responseRef = responseRef; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getExecutingAt() { return executingAt; }
    public void setExecutingAt(OffsetDateTime executingAt) { this.executingAt = executingAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(OffsetDateTime failedAt) { this.failedAt = failedAt; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public OffsetDateTime getRetryWaitingAt() { return retryWaitingAt; }
    public void setRetryWaitingAt(OffsetDateTime retryWaitingAt) { this.retryWaitingAt = retryWaitingAt; }
    public OffsetDateTime getExecutorUnavailableAt() { return executorUnavailableAt; }
    public void setExecutorUnavailableAt(OffsetDateTime executorUnavailableAt) { this.executorUnavailableAt = executorUnavailableAt; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public OffsetDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(OffsetDateTime claimedAt) { this.claimedAt = claimedAt; }
    public OffsetDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(OffsetDateTime leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public OffsetDateTime getWorkerHeartbeatAt() { return workerHeartbeatAt; }
    public void setWorkerHeartbeatAt(OffsetDateTime workerHeartbeatAt) { this.workerHeartbeatAt = workerHeartbeatAt; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = Math.max(0, attemptCount); }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(0, maxAttempts); }
    public String getExecutorName() { return executorName; }
    public void setExecutorName(String executorName) { this.executorName = executorName; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
