package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

/** P10.7 dual-control approval request for high-risk recovery operations. */
public class RecoveryApprovalRequest {
    private String approvalId;
    private RecoveryApprovalStatus status;
    private String action;
    private String targetType;
    private String targetId;
    private String dispatchRequestId;
    private String taskId;
    private String agentId;
    private String riskLevel;
    private String requestedBy;
    private String requesterPrincipal;
    private String requesterRole;
    private String requestReason;
    private String requestId;
    private String requestClientAddress;
    private String requestUserAgent;
    private String approvalReason;
    private String approvedBy;
    private String approverPrincipal;
    private String approverRole;
    private String approvalRequestId;
    private String approvalClientAddress;
    private String approvalUserAgent;
    private String rejectedBy;
    private String rejectedReason;
    private String cancelledBy;
    private String cancelledReason;
    private String executionResult;
    private String executionError;
    private OffsetDateTime expiresAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime executedAt;
    private OffsetDateTime rejectedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String payloadJson;

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }
    public RecoveryApprovalStatus getStatus() { return status; }
    public void setStatus(RecoveryApprovalStatus status) { this.status = status; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getRequesterPrincipal() { return requesterPrincipal; }
    public void setRequesterPrincipal(String requesterPrincipal) { this.requesterPrincipal = requesterPrincipal; }
    public String getRequesterRole() { return requesterRole; }
    public void setRequesterRole(String requesterRole) { this.requesterRole = requesterRole; }
    public String getRequestReason() { return requestReason; }
    public void setRequestReason(String requestReason) { this.requestReason = requestReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRequestClientAddress() { return requestClientAddress; }
    public void setRequestClientAddress(String requestClientAddress) { this.requestClientAddress = requestClientAddress; }
    public String getRequestUserAgent() { return requestUserAgent; }
    public void setRequestUserAgent(String requestUserAgent) { this.requestUserAgent = requestUserAgent; }
    public String getApprovalReason() { return approvalReason; }
    public void setApprovalReason(String approvalReason) { this.approvalReason = approvalReason; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getApproverPrincipal() { return approverPrincipal; }
    public void setApproverPrincipal(String approverPrincipal) { this.approverPrincipal = approverPrincipal; }
    public String getApproverRole() { return approverRole; }
    public void setApproverRole(String approverRole) { this.approverRole = approverRole; }
    public String getApprovalRequestId() { return approvalRequestId; }
    public void setApprovalRequestId(String approvalRequestId) { this.approvalRequestId = approvalRequestId; }
    public String getApprovalClientAddress() { return approvalClientAddress; }
    public void setApprovalClientAddress(String approvalClientAddress) { this.approvalClientAddress = approvalClientAddress; }
    public String getApprovalUserAgent() { return approvalUserAgent; }
    public void setApprovalUserAgent(String approvalUserAgent) { this.approvalUserAgent = approvalUserAgent; }
    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }
    public String getRejectedReason() { return rejectedReason; }
    public void setRejectedReason(String rejectedReason) { this.rejectedReason = rejectedReason; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelledReason() { return cancelledReason; }
    public void setCancelledReason(String cancelledReason) { this.cancelledReason = cancelledReason; }
    public String getExecutionResult() { return executionResult; }
    public void setExecutionResult(String executionResult) { this.executionResult = executionResult; }
    public String getExecutionError() { return executionError; }
    public void setExecutionError(String executionError) { this.executionError = executionError; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(OffsetDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
