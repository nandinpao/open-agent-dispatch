package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

/** Persisted, append-only timeline item for task dispatch/recovery diagnostics. */
public class DispatchAttemptHistoryRecord {
    private String historyId;
    private String taskId;
    private String incidentId;
    private String assignmentId;
    private String dispatchRequestId;
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String siteId;
    private String routingDecisionId;
    private String eventType;
    private String status;
    private Integer attemptNo;
    private Integer taskDispatchAttemptNo;
    private String reason;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime runtimeBackoffUntil;
    private String workerId;
    private OffsetDateTime claimUntil;
    private String payloadJson;
    private OffsetDateTime occurredAt;
    private OffsetDateTime createdAt;

    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getOwnerGatewayNodeId() { return ownerGatewayNodeId; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getRoutingDecisionId() { return routingDecisionId; }
    public void setRoutingDecisionId(String routingDecisionId) { this.routingDecisionId = routingDecisionId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public Integer getTaskDispatchAttemptNo() { return taskDispatchAttemptNo; }
    public void setTaskDispatchAttemptNo(Integer taskDispatchAttemptNo) { this.taskDispatchAttemptNo = taskDispatchAttemptNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public OffsetDateTime getRuntimeBackoffUntil() { return runtimeBackoffUntil; }
    public void setRuntimeBackoffUntil(OffsetDateTime runtimeBackoffUntil) { this.runtimeBackoffUntil = runtimeBackoffUntil; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public OffsetDateTime getClaimUntil() { return claimUntil; }
    public void setClaimUntil(OffsetDateTime claimUntil) { this.claimUntil = claimUntil; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
