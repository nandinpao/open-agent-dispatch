package com.opensocket.aievent.core.callback;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class TaskCallbackRecord {
    private String callbackId;
    private TaskCallbackType callbackType;
    private String taskId;
    private String dispatchRequestId;
    private String assignmentId;
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private Integer attemptNo;
    private String fencingToken;
    private boolean accepted = true;
    private String ignoredReason;
    private String message;
    private Integer progressPercent;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> payload = new LinkedHashMap<>();
    private OffsetDateTime occurredAt;
    private OffsetDateTime processedAt;
    private boolean duplicate;
    private String idempotencyKey;
    private String callbackFingerprint;
    private boolean replayDetected;
    private String previousTaskStatus;
    private String newTaskStatus;
    private String previousDispatchStatus;
    private String newDispatchStatus;

    public String getCallbackId() { return callbackId; }
    public void setCallbackId(String callbackId) { this.callbackId = callbackId; }
    public TaskCallbackType getCallbackType() { return callbackType; }
    public void setCallbackType(TaskCallbackType callbackType) { this.callbackType = callbackType; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getOwnerGatewayNodeId() { return ownerGatewayNodeId; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getFencingToken() { return fencingToken; }
    public void setFencingToken(String fencingToken) { this.fencingToken = fencingToken; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public String getIgnoredReason() { return ignoredReason; }
    public void setIgnoredReason(String ignoredReason) { this.ignoredReason = ignoredReason; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload); }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCallbackFingerprint() { return callbackFingerprint; }
    public void setCallbackFingerprint(String callbackFingerprint) { this.callbackFingerprint = callbackFingerprint; }
    public boolean isReplayDetected() { return replayDetected; }
    public void setReplayDetected(boolean replayDetected) { this.replayDetected = replayDetected; }
    public String getPreviousTaskStatus() { return previousTaskStatus; }
    public void setPreviousTaskStatus(String previousTaskStatus) { this.previousTaskStatus = previousTaskStatus; }
    public String getNewTaskStatus() { return newTaskStatus; }
    public void setNewTaskStatus(String newTaskStatus) { this.newTaskStatus = newTaskStatus; }
    public String getPreviousDispatchStatus() { return previousDispatchStatus; }
    public void setPreviousDispatchStatus(String previousDispatchStatus) { this.previousDispatchStatus = previousDispatchStatus; }
    public String getNewDispatchStatus() { return newDispatchStatus; }
    public void setNewDispatchStatus(String newDispatchStatus) { this.newDispatchStatus = newDispatchStatus; }
}
