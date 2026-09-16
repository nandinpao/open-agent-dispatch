package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Database-level compare-and-set transition request for dispatch_requests.
 *
 * <p>The dispatch row is the authoritative guard for callback/recovery concurrency.
 * Implementations must apply this as a single conditional update using current status,
 * attempt number, and dispatch token where available.</p>
 */
public class DispatchStatusTransition {
    private String dispatchRequestId;
    private List<DispatchRequestStatus> allowedCurrentStatuses = List.of();
    private DispatchRequestStatus newStatus;
    private Integer expectedAttemptNo;
    private String expectedDispatchToken;
    private String lastCallbackId;
    private String reason;
    private String lastError;
    private OffsetDateTime completedAt;
    private OffsetDateTime failedAt;
    private OffsetDateTime timedOutAt;
    private OffsetDateTime retryWaitingAt;
    private OffsetDateTime nextRetryAt;
    private OffsetDateTime updatedAt;
    private boolean clearClaim = true;
    private DispatchOutboxStatus outboxStatus;
    private String ackEvidenceId;
    private OffsetDateTime ackedAt;
    private DispatchRecoveryClassification recoveryClassification;
    private OffsetDateTime uncertainSince;
    private OffsetDateTime lastReconciledAt;
    private Integer reconciliationCountIncrement;

    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public List<DispatchRequestStatus> getAllowedCurrentStatuses() { return allowedCurrentStatuses; }
    public void setAllowedCurrentStatuses(List<DispatchRequestStatus> allowedCurrentStatuses) { this.allowedCurrentStatuses = allowedCurrentStatuses == null ? List.of() : allowedCurrentStatuses; }
    public DispatchRequestStatus getNewStatus() { return newStatus; }
    public void setNewStatus(DispatchRequestStatus newStatus) { this.newStatus = newStatus; }
    public Integer getExpectedAttemptNo() { return expectedAttemptNo; }
    public void setExpectedAttemptNo(Integer expectedAttemptNo) { this.expectedAttemptNo = expectedAttemptNo; }
    public String getExpectedDispatchToken() { return expectedDispatchToken; }
    public void setExpectedDispatchToken(String expectedDispatchToken) { this.expectedDispatchToken = expectedDispatchToken; }
    public String getLastCallbackId() { return lastCallbackId; }
    public void setLastCallbackId(String lastCallbackId) { this.lastCallbackId = lastCallbackId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(OffsetDateTime failedAt) { this.failedAt = failedAt; }
    public OffsetDateTime getTimedOutAt() { return timedOutAt; }
    public void setTimedOutAt(OffsetDateTime timedOutAt) { this.timedOutAt = timedOutAt; }
    public OffsetDateTime getRetryWaitingAt() { return retryWaitingAt; }
    public void setRetryWaitingAt(OffsetDateTime retryWaitingAt) { this.retryWaitingAt = retryWaitingAt; }
    public OffsetDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(OffsetDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isClearClaim() { return clearClaim; }
    public void setClearClaim(boolean clearClaim) { this.clearClaim = clearClaim; }
    public DispatchOutboxStatus getOutboxStatus() { return outboxStatus; }
    public void setOutboxStatus(DispatchOutboxStatus outboxStatus) { this.outboxStatus = outboxStatus; }
    public String getAckEvidenceId() { return ackEvidenceId; }
    public void setAckEvidenceId(String ackEvidenceId) { this.ackEvidenceId = ackEvidenceId; }
    public OffsetDateTime getAckedAt() { return ackedAt; }
    public void setAckedAt(OffsetDateTime ackedAt) { this.ackedAt = ackedAt; }
    public DispatchRecoveryClassification getRecoveryClassification() { return recoveryClassification; }
    public void setRecoveryClassification(DispatchRecoveryClassification recoveryClassification) { this.recoveryClassification = recoveryClassification; }
    public OffsetDateTime getUncertainSince() { return uncertainSince; }
    public void setUncertainSince(OffsetDateTime uncertainSince) { this.uncertainSince = uncertainSince; }
    public OffsetDateTime getLastReconciledAt() { return lastReconciledAt; }
    public void setLastReconciledAt(OffsetDateTime lastReconciledAt) { this.lastReconciledAt = lastReconciledAt; }
    public Integer getReconciliationCountIncrement() { return reconciliationCountIncrement; }
    public void setReconciliationCountIncrement(Integer reconciliationCountIncrement) { this.reconciliationCountIncrement = reconciliationCountIncrement; }

    public List<String> allowedCurrentStatusNames() {
        return allowedCurrentStatuses.stream().map(Enum::name).toList();
    }

    public String newStatusName() {
        return newStatus == null ? null : newStatus.name();
    }

    public String outboxStatusName() {
        return outboxStatus == null ? null : outboxStatus.name();
    }

    public String recoveryClassificationName() {
        return recoveryClassification == null ? null : recoveryClassification.name();
    }
}
