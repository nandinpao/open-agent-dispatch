package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "dispatch", name = "request-store", havingValue = "MEMORY")
public class InMemoryDispatchRequestRepository implements DispatchRequestRepository {
    private final ConcurrentMap<String, DispatchRequest> requests = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ClaimOwnership> claims = new ConcurrentHashMap<>();

    @Override
    public synchronized DispatchRequest save(DispatchRequest request) {
        synchronizeClaimState(request);
        requests.put(request.getDispatchRequestId(), request);
        return request;
    }

    @Override
    public Optional<DispatchRequest> findById(String dispatchRequestId) {
        return Optional.ofNullable(requests.get(dispatchRequestId));
    }

    @Override
    public Optional<DispatchRequest> findOpenByAssignmentId(String assignmentId) {
        return requests.values().stream()
                .filter(request -> assignmentId.equals(request.getAssignmentId()))
                .filter(request -> isOpen(request.getStatus()))
                .max(Comparator.comparing(DispatchRequest::getCreatedAt));
    }

    @Override
    public List<DispatchRequest> findByTaskId(String taskId, int limit) {
        return requests.values().stream()
                .filter(request -> taskId.equals(request.getTaskId()))
                .sorted(Comparator.comparing(DispatchRequest::getCreatedAt).reversed())
                .limit(cap(limit))
                .toList();
    }

    @Override
    public List<DispatchRequest> findByStatus(DispatchRequestStatus status, int limit) {
        return requests.values().stream()
                .filter(request -> status == request.getStatus())
                .sorted(Comparator.comparing(DispatchRequest::getCreatedAt).reversed())
                .limit(cap(limit))
                .toList();
    }

    @Override
    public synchronized Optional<DispatchRequest> claimById(
            String dispatchRequestId,
            ClaimRequest claimRequest) {
        DispatchRequest request = requests.get(dispatchRequestId);
        if (request == null || !claimable(request, claimRequest.now())) {
            return Optional.empty();
        }
        claim(request, claimRequest);
        return Optional.of(request);
    }

    @Override
    public synchronized List<DispatchRequest> claimExecutable(ClaimRequest claimRequest) {
        List<DispatchRequest> claimed = requests.values().stream()
                .filter(request -> claimable(request, claimRequest.now()))
                .sorted(Comparator.comparing(
                        DispatchRequest::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(claimRequest.limit())
                .toList();
        claimed.forEach(request -> claim(request, claimRequest));
        return new ArrayList<>(claimed);
    }

    @Override
    public synchronized PersistenceWriteResult saveClaimed(
            DispatchRequest request,
            ClaimOwnership ownership) {
        DispatchRequest current = requests.get(request.getDispatchRequestId());
        if (current == null) {
            return PersistenceWriteResult.notFound(request.getDispatchRequestId());
        }
        ClaimOwnership currentOwnership = claims.get(request.getDispatchRequestId());
        if (!ownership.equals(currentOwnership)) {
            return PersistenceWriteResult.ownershipLost(request.getDispatchRequestId());
        }
        claims.remove(request.getDispatchRequestId(), ownership);
        clearClaim(request);
        requests.put(request.getDispatchRequestId(), request);
        return PersistenceWriteResult.applied(request.getDispatchRequestId(), 1);
    }

    @Override
    public synchronized PersistenceWriteResult transitionStatus(DispatchStatusTransition transition) {
        DispatchRequest request = transition == null ? null : requests.get(transition.getDispatchRequestId());
        if (request == null) {
            return PersistenceWriteResult.notFound(transition == null ? null : transition.getDispatchRequestId());
        }
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
            clearClaim(request);
        }
        synchronizeClaimState(request);
        requests.put(request.getDispatchRequestId(), request);
        return PersistenceWriteResult.applied(transition.getDispatchRequestId(), 1);
    }

    @Override
    public List<DispatchRequest> recent(int limit) {
        return requests.values().stream()
                .sorted(Comparator.comparing(DispatchRequest::getCreatedAt).reversed())
                .limit(cap(limit))
                .toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private void claim(DispatchRequest request, ClaimRequest claimRequest) {
        request.setStatus(DispatchRequestStatus.DISPATCHING);
        request.setClaimedBy(claimRequest.workerId());
        request.setClaimStartedAt(claimRequest.now());
        request.setClaimUntil(claimRequest.claimUntil());
        request.setAttemptCount(request.getAttemptCount() + 1);
        request.setRetryWaitingAt(null);
        request.setNextRetryAt(null);
        request.setUpdatedAt(claimRequest.now());
        synchronizeClaimState(request);
        if (request.getCommand() != null) {
            request.getCommand().setAttemptNo(request.getAttemptCount());
        }
    }


    private void synchronizeClaimState(DispatchRequest request) {
        if (request != null
                && request.getStatus() == DispatchRequestStatus.DISPATCHING
                && request.getClaimedBy() != null
                && !request.getClaimedBy().isBlank()
                && request.getClaimUntil() != null) {
            claims.put(
                    request.getDispatchRequestId(),
                    new ClaimOwnership(request.getClaimedBy(), request.getClaimUntil()));
            return;
        }
        if (request != null) {
            claims.remove(request.getDispatchRequestId());
        }
    }

    private boolean claimable(DispatchRequest request, OffsetDateTime now) {
        if (request.getStatus() == DispatchRequestStatus.APPROVED) {
            return true;
        }
        if (request.getStatus() == DispatchRequestStatus.RETRY_WAITING) {
            return request.getNextRetryAt() == null || !request.getNextRetryAt().isAfter(now);
        }
        return request.getStatus() == DispatchRequestStatus.DISPATCHING
                && (request.getClaimUntil() == null || !request.getClaimUntil().isAfter(now));
    }

    private boolean owns(DispatchRequest request, ClaimOwnership ownership) {
        return request.getStatus() == DispatchRequestStatus.DISPATCHING
                && ownership.workerId().equals(request.getClaimedBy())
                && ownership.claimUntil().equals(request.getClaimUntil());
    }

    private void clearClaim(DispatchRequest request) {
        request.setClaimedBy(null);
        request.setClaimStartedAt(null);
        request.setClaimUntil(null);
    }

    private boolean isOpen(DispatchRequestStatus status) {
        return status == DispatchRequestStatus.PENDING_REVIEW
                || status == DispatchRequestStatus.APPROVED
                || status == DispatchRequestStatus.RETRY_WAITING
                || status == DispatchRequestStatus.DISPATCHING
                || status == DispatchRequestStatus.DISPATCHED
                || status == DispatchRequestStatus.ACKED
                || status == DispatchRequestStatus.RUNNING;
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit, 1000));
    }
}
