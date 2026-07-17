package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;

public interface DispatchRequestRepository {
    DispatchRequest save(DispatchRequest request);
    Optional<DispatchRequest> findById(String dispatchRequestId);
    Optional<DispatchRequest> findOpenByAssignmentId(String assignmentId);
    List<DispatchRequest> findByTaskId(String taskId, int limit);
    List<DispatchRequest> findByStatus(DispatchRequestStatus status, int limit);
    Optional<DispatchRequest> claimById(String dispatchRequestId, ClaimRequest claimRequest);
    List<DispatchRequest> claimExecutable(ClaimRequest claimRequest);
    PersistenceWriteResult saveClaimed(DispatchRequest request, ClaimOwnership ownership);

    default PersistenceWriteResult transitionStatus(DispatchStatusTransition transition) {
        if (transition == null || transition.getDispatchRequestId() == null || transition.getDispatchRequestId().isBlank()) {
            throw new IllegalArgumentException("dispatchRequestId is required");
        }
        Optional<DispatchRequest> current = findById(transition.getDispatchRequestId());
        if (current.isEmpty()) {
            return PersistenceWriteResult.notFound(transition.getDispatchRequestId());
        }
        DispatchRequest request = current.get();
        if (transition.getAllowedCurrentStatuses().isEmpty()
                || !transition.getAllowedCurrentStatuses().contains(request.getStatus())) {
            return PersistenceWriteResult.conflict(transition.getDispatchRequestId());
        }
        if (transition.getExpectedAttemptNo() != null
                && transition.getExpectedAttemptNo() != request.getAttemptCount()) {
            return PersistenceWriteResult.conflict(transition.getDispatchRequestId());
        }
        if (transition.getExpectedDispatchToken() != null
                && !transition.getExpectedDispatchToken().isBlank()
                && !transition.getExpectedDispatchToken().equals(request.getDispatchToken())) {
            return PersistenceWriteResult.conflict(transition.getDispatchRequestId());
        }
        request.setStatus(transition.getNewStatus());
        request.setReason(transition.getReason());
        request.setLastError(transition.getLastError());
        request.setLastCallbackId(transition.getLastCallbackId());
        request.setCompletedAt(transition.getCompletedAt());
        request.setFailedAt(transition.getFailedAt());
        request.setTimedOutAt(transition.getTimedOutAt());
        request.setRetryWaitingAt(transition.getRetryWaitingAt());
        request.setNextRetryAt(transition.getNextRetryAt());
        request.setUpdatedAt(transition.getUpdatedAt());
        if (transition.isClearClaim()) {
            request.setClaimedBy(null);
            request.setClaimStartedAt(null);
            request.setClaimUntil(null);
        }
        save(request);
        return PersistenceWriteResult.applied(transition.getDispatchRequestId(), 1);
    }

    List<DispatchRequest> recent(int limit);
    String mode();
}
