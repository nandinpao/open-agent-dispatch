package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

public class DispatchRequest {
    private String dispatchRequestId;
    private String assignmentId;
    private String taskId;
    private String incidentId;
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String siteId;
    private DispatchRequestStatus status;
    private DispatchReviewMode reviewMode;
    private DispatchEligibilityStatus eligibilityStatus;
    private DispatchMethod dispatchMethod;
    private String gatewayDispatchPath;
    private String dispatchToken;
    private String reason;
    private NettyDispatchCommand command;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime dispatchedAt;
    private OffsetDateTime failedAt;
    private int attemptCount;
    private String lastError;
    private String lastCallbackId;
    private OffsetDateTime completedAt;
    private OffsetDateTime timedOutAt;
    private OffsetDateTime retryWaitingAt;
    private OffsetDateTime nextRetryAt;
    private OffsetDateTime deadLetterAt;
    private String claimedBy;
    private OffsetDateTime claimStartedAt;
    private OffsetDateTime claimUntil;

    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getOwnerGatewayNodeId() { return ownerGatewayNodeId; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public DispatchRequestStatus getStatus() { return status; }
    public void setStatus(DispatchRequestStatus status) { this.status = status; }
    public DispatchReviewMode getReviewMode() { return reviewMode; }
    public void setReviewMode(DispatchReviewMode reviewMode) { this.reviewMode = reviewMode; }
    public DispatchEligibilityStatus getEligibilityStatus() { return eligibilityStatus; }
    public void setEligibilityStatus(DispatchEligibilityStatus eligibilityStatus) { this.eligibilityStatus = eligibilityStatus; }
    public DispatchMethod getDispatchMethod() { return dispatchMethod; }
    public void setDispatchMethod(DispatchMethod dispatchMethod) { this.dispatchMethod = dispatchMethod; }
    public String getGatewayDispatchPath() { return gatewayDispatchPath; }
    public void setGatewayDispatchPath(String gatewayDispatchPath) { this.gatewayDispatchPath = gatewayDispatchPath; }
    public String getDispatchToken() { return dispatchToken; }
    public void setDispatchToken(String dispatchToken) { this.dispatchToken = dispatchToken; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public NettyDispatchCommand getCommand() { return command; }
    public void setCommand(NettyDispatchCommand command) { this.command = command; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public OffsetDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(OffsetDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public OffsetDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(OffsetDateTime failedAt) { this.failedAt = failedAt; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = Math.max(0, attemptCount); }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getLastCallbackId() { return lastCallbackId; }
    public void setLastCallbackId(String lastCallbackId) { this.lastCallbackId = lastCallbackId; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getTimedOutAt() { return timedOutAt; }
    public void setTimedOutAt(OffsetDateTime timedOutAt) { this.timedOutAt = timedOutAt; }
    public OffsetDateTime getRetryWaitingAt() { return retryWaitingAt; }
    public void setRetryWaitingAt(OffsetDateTime retryWaitingAt) { this.retryWaitingAt = retryWaitingAt; }
    public OffsetDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(OffsetDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public OffsetDateTime getDeadLetterAt() { return deadLetterAt; }
    public void setDeadLetterAt(OffsetDateTime deadLetterAt) { this.deadLetterAt = deadLetterAt; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public OffsetDateTime getClaimStartedAt() { return claimStartedAt; }
    public void setClaimStartedAt(OffsetDateTime claimStartedAt) { this.claimStartedAt = claimStartedAt; }
    public OffsetDateTime getClaimUntil() { return claimUntil; }
    public void setClaimUntil(OffsetDateTime claimUntil) { this.claimUntil = claimUntil; }
}


