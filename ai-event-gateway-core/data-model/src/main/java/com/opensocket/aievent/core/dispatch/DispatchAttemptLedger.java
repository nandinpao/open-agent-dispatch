package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable, query-oriented dispatch ledger view.
 *
 * <p>This is not Gateway node telemetry. It is derived from Core dispatch rows and persisted
 * callback records, so it survives single/cluster topology changes and node restarts.</p>
 */
public class DispatchAttemptLedger {
    private String dispatchRequestId;
    private String taskId;
    private String incidentId;
    private String assignmentId;
    private String agentId;
    private String lastKnownGatewayNodeId;
    private String lastKnownAgentSessionId;
    private String dispatchStatus;
    private String deliveryState;
    private String callbackState;
    private String resultState;
    private Integer attemptNo;
    private String lastCallbackId;
    private boolean dispatchTokenPresent;
    private boolean authoritative = true;
    private boolean recoveryRequired;
    private String nextAction;
    private String lastError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime dispatchedAt;
    private OffsetDateTime ackReceivedAt;
    private OffsetDateTime progressReceivedAt;
    private OffsetDateTime resultReceivedAt;
    private OffsetDateTime errorReceivedAt;
    private OffsetDateTime terminalAt;
    private OffsetDateTime leaseExpiresAt;
    private OffsetDateTime callbackDeadlineAt;
    private List<DispatchAttemptLedgerEvent> events = new ArrayList<>();

    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getLastKnownGatewayNodeId() { return lastKnownGatewayNodeId; }
    public void setLastKnownGatewayNodeId(String lastKnownGatewayNodeId) { this.lastKnownGatewayNodeId = lastKnownGatewayNodeId; }
    public String getLastKnownAgentSessionId() { return lastKnownAgentSessionId; }
    public void setLastKnownAgentSessionId(String lastKnownAgentSessionId) { this.lastKnownAgentSessionId = lastKnownAgentSessionId; }
    public String getDispatchStatus() { return dispatchStatus; }
    public void setDispatchStatus(String dispatchStatus) { this.dispatchStatus = dispatchStatus; }
    public String getDeliveryState() { return deliveryState; }
    public void setDeliveryState(String deliveryState) { this.deliveryState = deliveryState; }
    public String getCallbackState() { return callbackState; }
    public void setCallbackState(String callbackState) { this.callbackState = callbackState; }
    public String getResultState() { return resultState; }
    public void setResultState(String resultState) { this.resultState = resultState; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getLastCallbackId() { return lastCallbackId; }
    public void setLastCallbackId(String lastCallbackId) { this.lastCallbackId = lastCallbackId; }
    public boolean isDispatchTokenPresent() { return dispatchTokenPresent; }
    public void setDispatchTokenPresent(boolean dispatchTokenPresent) { this.dispatchTokenPresent = dispatchTokenPresent; }
    public boolean isAuthoritative() { return authoritative; }
    public void setAuthoritative(boolean authoritative) { this.authoritative = authoritative; }
    public boolean isRecoveryRequired() { return recoveryRequired; }
    public void setRecoveryRequired(boolean recoveryRequired) { this.recoveryRequired = recoveryRequired; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(OffsetDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public OffsetDateTime getAckReceivedAt() { return ackReceivedAt; }
    public void setAckReceivedAt(OffsetDateTime ackReceivedAt) { this.ackReceivedAt = ackReceivedAt; }
    public OffsetDateTime getProgressReceivedAt() { return progressReceivedAt; }
    public void setProgressReceivedAt(OffsetDateTime progressReceivedAt) { this.progressReceivedAt = progressReceivedAt; }
    public OffsetDateTime getResultReceivedAt() { return resultReceivedAt; }
    public void setResultReceivedAt(OffsetDateTime resultReceivedAt) { this.resultReceivedAt = resultReceivedAt; }
    public OffsetDateTime getErrorReceivedAt() { return errorReceivedAt; }
    public void setErrorReceivedAt(OffsetDateTime errorReceivedAt) { this.errorReceivedAt = errorReceivedAt; }
    public OffsetDateTime getTerminalAt() { return terminalAt; }
    public void setTerminalAt(OffsetDateTime terminalAt) { this.terminalAt = terminalAt; }
    public OffsetDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(OffsetDateTime leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public OffsetDateTime getCallbackDeadlineAt() { return callbackDeadlineAt; }
    public void setCallbackDeadlineAt(OffsetDateTime callbackDeadlineAt) { this.callbackDeadlineAt = callbackDeadlineAt; }
    public List<DispatchAttemptLedgerEvent> getEvents() { return events; }
    public void setEvents(List<DispatchAttemptLedgerEvent> events) { this.events = events == null ? new ArrayList<>() : new ArrayList<>(events); }
}
