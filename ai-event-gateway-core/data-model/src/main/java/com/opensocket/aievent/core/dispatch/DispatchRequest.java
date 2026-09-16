package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

public class DispatchRequest {
    private String dispatchRequestId;
    private String tenantId;
    private String assignmentId;
    private String executionAuthorityVersion = "LEGACY";
    private String canonicalExecutionAssignmentId;
    private DispatchAuthorityProvenance authorityProvenance = DispatchAuthorityProvenance.LEGACY_COMPATIBILITY;
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
    private DispatchOutboxStatus outboxStatus = DispatchOutboxStatus.PENDING;
    private String claimToken;
    private OffsetDateTime claimHeartbeatAt;
    private String dispatchTokenHash;
    private String fencingTokenHash;
    private String runtimeSessionId;
    private String ackEvidenceId;
    private OffsetDateTime ackedAt;
    private DispatchRecoveryClassification recoveryClassification = DispatchRecoveryClassification.NONE;
    private OffsetDateTime uncertainSince;
    private OffsetDateTime lastReconciledAt;
    private int reconciliationCount;
    private long rowVersion;

    public String getDispatchRequestId() { return dispatchRequestId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getExecutionAuthorityVersion() { return executionAuthorityVersion; }
    public void setExecutionAuthorityVersion(String executionAuthorityVersion) { this.executionAuthorityVersion = executionAuthorityVersion == null || executionAuthorityVersion.isBlank() ? "LEGACY" : executionAuthorityVersion; }
    public String getCanonicalExecutionAssignmentId() { return canonicalExecutionAssignmentId; }
    public void setCanonicalExecutionAssignmentId(String canonicalExecutionAssignmentId) { this.canonicalExecutionAssignmentId = canonicalExecutionAssignmentId; }
    public DispatchAuthorityProvenance getAuthorityProvenance() { return authorityProvenance; }
    public void setAuthorityProvenance(DispatchAuthorityProvenance authorityProvenance) { this.authorityProvenance = authorityProvenance == null ? DispatchAuthorityProvenance.LEGACY_COMPATIBILITY : authorityProvenance; }
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
    public DispatchOutboxStatus getOutboxStatus() { return outboxStatus; }
    public void setOutboxStatus(DispatchOutboxStatus outboxStatus) { this.outboxStatus = outboxStatus == null ? DispatchOutboxStatus.PENDING : outboxStatus; }
    public String getClaimToken() { return claimToken; }
    public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
    public OffsetDateTime getClaimHeartbeatAt() { return claimHeartbeatAt; }
    public void setClaimHeartbeatAt(OffsetDateTime claimHeartbeatAt) { this.claimHeartbeatAt = claimHeartbeatAt; }
    public String getDispatchTokenHash() { return dispatchTokenHash; }
    public void setDispatchTokenHash(String dispatchTokenHash) { this.dispatchTokenHash = dispatchTokenHash; }
    public String getFencingTokenHash() { return fencingTokenHash; }
    public void setFencingTokenHash(String fencingTokenHash) { this.fencingTokenHash = fencingTokenHash; }
    public String getRuntimeSessionId() { return runtimeSessionId; }
    public void setRuntimeSessionId(String runtimeSessionId) { this.runtimeSessionId = runtimeSessionId; }
    public String getAckEvidenceId() { return ackEvidenceId; }
    public void setAckEvidenceId(String ackEvidenceId) { this.ackEvidenceId = ackEvidenceId; }
    public OffsetDateTime getAckedAt() { return ackedAt; }
    public void setAckedAt(OffsetDateTime ackedAt) { this.ackedAt = ackedAt; }
    public DispatchRecoveryClassification getRecoveryClassification() { return recoveryClassification; }
    public void setRecoveryClassification(DispatchRecoveryClassification recoveryClassification) { this.recoveryClassification = recoveryClassification == null ? DispatchRecoveryClassification.NONE : recoveryClassification; }
    public OffsetDateTime getUncertainSince() { return uncertainSince; }
    public void setUncertainSince(OffsetDateTime uncertainSince) { this.uncertainSince = uncertainSince; }
    public OffsetDateTime getLastReconciledAt() { return lastReconciledAt; }
    public void setLastReconciledAt(OffsetDateTime lastReconciledAt) { this.lastReconciledAt = lastReconciledAt; }
    public int getReconciliationCount() { return reconciliationCount; }
    public void setReconciliationCount(int reconciliationCount) { this.reconciliationCount = Math.max(0, reconciliationCount); }
    public long getRowVersion() { return rowVersion; }
    public void setRowVersion(long rowVersion) { this.rowVersion = Math.max(0L, rowVersion); }
}



