package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;

/**
 * Mutable cancellation aggregate used by application and persistence ports.
 *
 * <p>This contract deliberately provides explicit accessors rather than relying on
 * Lombok-generated methods. The a2a-contracts module is a foundational reactor
 * module and must compile deterministically even when annotation processing is
 * constrained by the build toolchain.</p>
 */
public class A2ACancellationRecord {
    private String tenantId;
    private String cancellationId;
    private String requestId;
    private String childTaskId;
    private String assignmentId;
    private String executionAttemptId;
    private Integer attemptNo;
    private String dispatchRequestId;
    private String agentId;
    private String agentSessionId;
    private String ownerGatewayNodeId;
    private String revokedFencingTokenHash;
    private String activeFencingTokenHash;
    private String cancellationFingerprint;
    private String idempotencyKey;
    private A2ACancellationStatus status = A2ACancellationStatus.REQUESTED;
    private A2ACancellationOutcome outcome = A2ACancellationOutcome.PENDING;
    private A2ACancellationProcessingStatus processingStatus = A2ACancellationProcessingStatus.REQUESTED;
    private A2ACancellationReconciliationClassification reconciliationClassification =
            A2ACancellationReconciliationClassification.NONE;
    private String reason;
    private String requestedByType;
    private String requestedById;
    private String deliveryStatus;
    private String lastError;
    private int retryCount;
    private int reconciliationCount;
    private OffsetDateTime requestedAt;
    private OffsetDateTime resultCutoffAt;
    private OffsetDateTime deliveryAt;
    private OffsetDateTime acknowledgedAt;
    private OffsetDateTime deadlineAt;
    private OffsetDateTime nextReconcileAt;
    private OffsetDateTime lastReconciledAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime updatedAt;
    private long version = 1L;

    public A2ACancellationRecord() {}

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getCancellationId() { return cancellationId; }
    public void setCancellationId(String cancellationId) { this.cancellationId = cancellationId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getChildTaskId() { return childTaskId; }
    public void setChildTaskId(String childTaskId) { this.childTaskId = childTaskId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getExecutionAttemptId() { return executionAttemptId; }
    public void setExecutionAttemptId(String executionAttemptId) { this.executionAttemptId = executionAttemptId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getOwnerGatewayNodeId() { return ownerGatewayNodeId; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public String getRevokedFencingTokenHash() { return revokedFencingTokenHash; }
    public void setRevokedFencingTokenHash(String revokedFencingTokenHash) { this.revokedFencingTokenHash = revokedFencingTokenHash; }
    public String getActiveFencingTokenHash() { return activeFencingTokenHash; }
    public void setActiveFencingTokenHash(String activeFencingTokenHash) { this.activeFencingTokenHash = activeFencingTokenHash; }
    public String getCancellationFingerprint() { return cancellationFingerprint; }
    public void setCancellationFingerprint(String cancellationFingerprint) { this.cancellationFingerprint = cancellationFingerprint; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public A2ACancellationStatus getStatus() { return status; }
    public void setStatus(A2ACancellationStatus status) { this.status = status; }
    public A2ACancellationOutcome getOutcome() { return outcome; }
    public void setOutcome(A2ACancellationOutcome outcome) { this.outcome = outcome; }
    public A2ACancellationProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(A2ACancellationProcessingStatus processingStatus) { this.processingStatus = processingStatus; }
    public A2ACancellationReconciliationClassification getReconciliationClassification() { return reconciliationClassification; }
    public void setReconciliationClassification(A2ACancellationReconciliationClassification reconciliationClassification) { this.reconciliationClassification = reconciliationClassification; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestedByType() { return requestedByType; }
    public void setRequestedByType(String requestedByType) { this.requestedByType = requestedByType; }
    public String getRequestedById() { return requestedById; }
    public void setRequestedById(String requestedById) { this.requestedById = requestedById; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getReconciliationCount() { return reconciliationCount; }
    public void setReconciliationCount(int reconciliationCount) { this.reconciliationCount = reconciliationCount; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
    public OffsetDateTime getResultCutoffAt() { return resultCutoffAt; }
    public void setResultCutoffAt(OffsetDateTime resultCutoffAt) { this.resultCutoffAt = resultCutoffAt; }
    public OffsetDateTime getDeliveryAt() { return deliveryAt; }
    public void setDeliveryAt(OffsetDateTime deliveryAt) { this.deliveryAt = deliveryAt; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public OffsetDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(OffsetDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public OffsetDateTime getNextReconcileAt() { return nextReconcileAt; }
    public void setNextReconcileAt(OffsetDateTime nextReconcileAt) { this.nextReconcileAt = nextReconcileAt; }
    public OffsetDateTime getLastReconciledAt() { return lastReconciledAt; }
    public void setLastReconciledAt(OffsetDateTime lastReconciledAt) { this.lastReconciledAt = lastReconciledAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
