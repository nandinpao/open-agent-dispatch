package com.opensocket.aievent.core.callback;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Durable callback inbox view used by Admin UI and recovery services.
 *
 * <p>This is a view over persisted {@link TaskCallbackRecord} rows. It deliberately models
 * callback receiving / processing state as Core truth, not Gateway node runtime telemetry. A
 * callback may be received by any available Gateway after topology changes; idempotency and
 * processing status are resolved from the persisted callback record.</p>
 */
public class CallbackInboxEntry {
    private String callbackId;
    private String taskId;
    private String dispatchRequestId;
    private String assignmentId;
    private String agentId;
    private String receivedByGatewayNodeId;
    private String receivedAgentSessionId;
    private Integer attemptNo;
    private String callbackType;
    private String processStatus;
    private boolean accepted;
    private boolean duplicate;
    private boolean replayDetected;
    private String idempotencyKey;
    private String callbackFingerprint;
    private String ignoredReason;
    private String message;
    private String errorCode;
    private String errorMessage;
    private String previousTaskStatus;
    private String newTaskStatus;
    private String previousDispatchStatus;
    private String newDispatchStatus;
    private Map<String, Object> payload = new LinkedHashMap<>();
    private OffsetDateTime occurredAt;
    private OffsetDateTime receivedAt;
    private OffsetDateTime processedAt;
    private boolean authoritative = true;

    public static CallbackInboxEntry from(TaskCallbackRecord record) {
        CallbackInboxEntry entry = new CallbackInboxEntry();
        if (record == null) {
            return entry;
        }
        entry.setCallbackId(record.getCallbackId());
        entry.setTaskId(record.getTaskId());
        entry.setDispatchRequestId(record.getDispatchRequestId());
        entry.setAssignmentId(record.getAssignmentId());
        entry.setAgentId(record.getAgentId());
        entry.setReceivedByGatewayNodeId(record.getOwnerGatewayNodeId());
        entry.setReceivedAgentSessionId(record.getAgentSessionId());
        entry.setAttemptNo(record.getAttemptNo());
        entry.setCallbackType(record.getCallbackType() == null ? null : record.getCallbackType().name());
        entry.setAccepted(record.isAccepted());
        entry.setDuplicate(record.isDuplicate());
        entry.setReplayDetected(record.isReplayDetected());
        entry.setIdempotencyKey(record.getIdempotencyKey());
        entry.setCallbackFingerprint(record.getCallbackFingerprint());
        entry.setIgnoredReason(record.getIgnoredReason());
        entry.setMessage(record.getMessage());
        entry.setErrorCode(record.getErrorCode());
        entry.setErrorMessage(record.getErrorMessage());
        entry.setPreviousTaskStatus(record.getPreviousTaskStatus());
        entry.setNewTaskStatus(record.getNewTaskStatus());
        entry.setPreviousDispatchStatus(record.getPreviousDispatchStatus());
        entry.setNewDispatchStatus(record.getNewDispatchStatus());
        entry.setPayload(record.getPayload());
        entry.setOccurredAt(record.getOccurredAt());
        entry.setReceivedAt(record.getOccurredAt());
        entry.setProcessedAt(record.getProcessedAt());
        entry.setProcessStatus(processStatus(record));
        return entry;
    }

    private static String processStatus(TaskCallbackRecord record) {
        if (record == null) return "UNKNOWN";
        if (record.isReplayDetected()) return "REPLAY_REJECTED";
        if (record.isDuplicate()) return "DUPLICATE_IGNORED";
        if (!record.isAccepted()) return "REJECTED";
        if (record.getProcessedAt() != null) return "PROCESSED";
        return "RECEIVED";
    }

    public String getCallbackId() { return callbackId; }
    public void setCallbackId(String callbackId) { this.callbackId = callbackId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getReceivedByGatewayNodeId() { return receivedByGatewayNodeId; }
    public void setReceivedByGatewayNodeId(String receivedByGatewayNodeId) { this.receivedByGatewayNodeId = receivedByGatewayNodeId; }
    public String getReceivedAgentSessionId() { return receivedAgentSessionId; }
    public void setReceivedAgentSessionId(String receivedAgentSessionId) { this.receivedAgentSessionId = receivedAgentSessionId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getCallbackType() { return callbackType; }
    public void setCallbackType(String callbackType) { this.callbackType = callbackType; }
    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String processStatus) { this.processStatus = processStatus; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
    public boolean isReplayDetected() { return replayDetected; }
    public void setReplayDetected(boolean replayDetected) { this.replayDetected = replayDetected; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCallbackFingerprint() { return callbackFingerprint; }
    public void setCallbackFingerprint(String callbackFingerprint) { this.callbackFingerprint = callbackFingerprint; }
    public String getIgnoredReason() { return ignoredReason; }
    public void setIgnoredReason(String ignoredReason) { this.ignoredReason = ignoredReason; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getPreviousTaskStatus() { return previousTaskStatus; }
    public void setPreviousTaskStatus(String previousTaskStatus) { this.previousTaskStatus = previousTaskStatus; }
    public String getNewTaskStatus() { return newTaskStatus; }
    public void setNewTaskStatus(String newTaskStatus) { this.newTaskStatus = newTaskStatus; }
    public String getPreviousDispatchStatus() { return previousDispatchStatus; }
    public void setPreviousDispatchStatus(String previousDispatchStatus) { this.previousDispatchStatus = previousDispatchStatus; }
    public String getNewDispatchStatus() { return newDispatchStatus; }
    public void setNewDispatchStatus(String newDispatchStatus) { this.newDispatchStatus = newDispatchStatus; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload); }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
    public boolean isAuthoritative() { return authoritative; }
    public void setAuthoritative(boolean authoritative) { this.authoritative = authoritative; }
}
