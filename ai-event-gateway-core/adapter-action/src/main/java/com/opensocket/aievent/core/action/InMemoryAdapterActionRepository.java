package com.opensocket.aievent.core.action;

import java.time.OffsetDateTime;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.LeaseRenewalRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "adapter-actions", name = "store", havingValue = "MEMORY")
public class InMemoryAdapterActionRepository implements AdapterActionRepository {
    private final ConcurrentMap<String, AdapterAction> actions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> actionIdByIdempotencyKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ClaimOwnership> claims = new ConcurrentHashMap<>();

    @Override
    public synchronized AdapterAction save(AdapterAction action) {
        AdapterAction stored = copy(action);
        synchronizeClaimState(stored);
        actions.put(stored.getActionId(), stored);
        if (stored.getIdempotencyKey() != null && !stored.getIdempotencyKey().isBlank()) {
            actionIdByIdempotencyKey.put(stored.getIdempotencyKey(), stored.getActionId());
        }
        return copy(stored);
    }

    @Override
    public synchronized AdapterAction saveNewOrGetByIdempotencyKey(AdapterAction action) {
        if (action.getIdempotencyKey() == null || action.getIdempotencyKey().isBlank()) {
            return save(action);
        }
        String existingId = actionIdByIdempotencyKey.putIfAbsent(action.getIdempotencyKey(), action.getActionId());
        if (existingId != null) {
            return copy(actions.get(existingId));
        }
        AdapterAction stored = copy(action);
        actions.put(stored.getActionId(), stored);
        synchronizeClaimState(stored);
        return copy(stored);
    }

    @Override
    public Optional<AdapterAction> findById(String actionId) {
        return Optional.ofNullable(copy(actions.get(actionId)));
    }

    @Override
    public Optional<AdapterAction> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String actionId = actionIdByIdempotencyKey.get(idempotencyKey);
        return actionId == null ? Optional.empty() : findById(actionId);
    }

    @Override
    public List<AdapterAction> findByIncidentId(String incidentId, int limit) {
        return actions.values().stream()
                .filter(a -> incidentId != null && incidentId.equals(a.getIncidentId()))
                .sorted(Comparator.comparing(AdapterAction::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<AdapterAction> findByTaskId(String taskId, int limit) {
        return actions.values().stream()
                .filter(a -> taskId != null && taskId.equals(a.getTaskId()))
                .sorted(Comparator.comparing(AdapterAction::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<AdapterAction> findByStatus(AdapterActionStatus status, int limit) {
        return actions.values().stream()
                .filter(a -> status == a.getStatus())
                .sorted(Comparator.comparing(AdapterAction::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<AdapterAction> findExecutablePending(OffsetDateTime now, int limit) {
        return actions.values().stream()
                .filter(a -> a.getStatus() == AdapterActionStatus.PENDING || a.getStatus() == AdapterActionStatus.RETRY_WAITING || a.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE)
                .filter(a -> a.getNextAttemptAt() == null || !a.getNextAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(AdapterAction::getCreatedAt))
                .limit(Math.max(1, Math.min(limit, 1000)))
                .map(this::copy)
                .toList();
    }

    @Override
    public synchronized Optional<AdapterAction> claimNext(AdapterType adapterType, ClaimRequest request) {
        Optional<AdapterAction> candidate = actions.values().stream()
                .filter(action -> adapterType == null || adapterType == action.getAdapterType())
                .filter(action -> isClaimable(action, request.now()))
                .filter(action -> action.getNextAttemptAt() == null || !action.getNextAttemptAt().isAfter(request.now()))
                .sorted(Comparator.comparing(AdapterAction::getCreatedAt))
                .findFirst();
        candidate.ifPresent(action -> {
            action.setStatus(AdapterActionStatus.CLAIMED);
            action.setClaimedBy(request.workerId());
            action.setClaimedAt(request.now());
            action.setWorkerHeartbeatAt(request.now());
            action.setLeaseExpiresAt(request.claimUntil());
            action.setUpdatedAt(request.now());
            actions.put(action.getActionId(), action);
            synchronizeClaimState(action);
        });
        return candidate.map(this::copy);
    }

    @Override
    public synchronized Optional<AdapterAction> extendLease(LeaseRenewalRequest request) {
        AdapterAction action = actions.get(request.resourceId());
        if (action == null
                || action.getStatus() != AdapterActionStatus.CLAIMED
                || !owns(action, request.ownership())
                || (action.getLeaseExpiresAt() != null && action.getLeaseExpiresAt().isBefore(request.now()))) {
            return Optional.empty();
        }
        action.setWorkerHeartbeatAt(request.now());
        action.setLeaseExpiresAt(request.newClaimUntil());
        action.setUpdatedAt(request.now());
        actions.put(action.getActionId(), action);
        claims.put(action.getActionId(), new ClaimOwnership(request.ownership().workerId(), request.newClaimUntil()));
        return Optional.of(copy(action));
    }

    @Override
    public synchronized PersistenceWriteResult saveClaimed(
            AdapterAction action,
            ClaimOwnership ownership,
            OffsetDateTime now) {
        AdapterAction current = actions.get(action.getActionId());
        if (current == null) {
            return PersistenceWriteResult.notFound(action.getActionId());
        }
        ClaimOwnership currentOwnership = claims.get(action.getActionId());
        if (!ownership.equals(currentOwnership)
                || ownership.claimUntil().isBefore(now)) {
            return PersistenceWriteResult.ownershipLost(action.getActionId());
        }
        claims.remove(action.getActionId(), ownership);
        AdapterAction stored = copy(action);
        clearClaim(stored);
        actions.put(stored.getActionId(), stored);
        return PersistenceWriteResult.applied(stored.getActionId(), 1);
    }

    @Override
    public synchronized PersistenceWriteResult recoverExpiredClaim(
            AdapterAction action,
            ClaimOwnership ownership,
            OffsetDateTime observedAt) {
        AdapterAction current = actions.get(action.getActionId());
        if (current == null) {
            return PersistenceWriteResult.notFound(action.getActionId());
        }
        ClaimOwnership currentOwnership = claims.get(action.getActionId());
        if (!ownership.equals(currentOwnership)
                || ownership.claimUntil().isAfter(observedAt)) {
            return PersistenceWriteResult.ownershipLost(action.getActionId());
        }
        claims.remove(action.getActionId(), ownership);
        AdapterAction stored = copy(action);
        clearClaim(stored);
        actions.put(stored.getActionId(), stored);
        return PersistenceWriteResult.applied(stored.getActionId(), 1);
    }


    private void synchronizeClaimState(AdapterAction action) {
        if (action != null
                && action.getStatus() == AdapterActionStatus.CLAIMED
                && action.getClaimedBy() != null
                && !action.getClaimedBy().isBlank()
                && action.getLeaseExpiresAt() != null) {
            claims.put(
                    action.getActionId(),
                    new ClaimOwnership(action.getClaimedBy(), action.getLeaseExpiresAt()));
            return;
        }
        if (action != null) {
            claims.remove(action.getActionId());
        }
    }

    private boolean owns(AdapterAction action, ClaimOwnership ownership) {
        return action.getStatus() == AdapterActionStatus.CLAIMED
                && ownership.workerId().equals(action.getClaimedBy())
                && ownership.claimUntil().equals(action.getLeaseExpiresAt());
    }

    private void clearClaim(AdapterAction action) {
        action.setClaimedBy(null);
        action.setClaimedAt(null);
        action.setLeaseExpiresAt(null);
        action.setWorkerHeartbeatAt(null);
    }

    private boolean isClaimable(AdapterAction action, OffsetDateTime now) {
        if (action.getStatus() == AdapterActionStatus.PENDING || action.getStatus() == AdapterActionStatus.RETRY_WAITING || action.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE) {
            return true;
        }
        return action.getStatus() == AdapterActionStatus.CLAIMED
                && action.getLeaseExpiresAt() != null
                && !action.getLeaseExpiresAt().isAfter(now);
    }

    @Override
    public List<AdapterAction> recent(int limit) {
        return actions.values().stream()
                .sorted(Comparator.comparing(AdapterAction::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .map(this::copy)
                .toList();
    }

    @Override
    public String mode() { return "MEMORY"; }

    private AdapterAction copy(AdapterAction source) {
        if (source == null) return null;
        AdapterAction copy = new AdapterAction();
        copy.setActionId(source.getActionId());
        copy.setIdempotencyKey(source.getIdempotencyKey());
        copy.setIncidentId(source.getIncidentId());
        copy.setTaskId(source.getTaskId());
        copy.setDispatchRequestId(source.getDispatchRequestId());
        copy.setAssignmentId(source.getAssignmentId());
        copy.setAgentId(source.getAgentId());
        copy.setAdapterName(source.getAdapterName());
        copy.setAdapterType(source.getAdapterType());
        copy.setActionType(source.getActionType());
        copy.setStatus(source.getStatus());
        copy.setReason(source.getReason());
        copy.setRequestHash(source.getRequestHash());
        copy.setResponseRef(source.getResponseRef());
        copy.setPayload(source.getPayload());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setExecutingAt(source.getExecutingAt());
        copy.setCompletedAt(source.getCompletedAt());
        copy.setFailedAt(source.getFailedAt());
        copy.setNextAttemptAt(source.getNextAttemptAt());
        copy.setRetryWaitingAt(source.getRetryWaitingAt());
        copy.setExecutorUnavailableAt(source.getExecutorUnavailableAt());
        copy.setClaimedBy(source.getClaimedBy());
        copy.setClaimedAt(source.getClaimedAt());
        copy.setLeaseExpiresAt(source.getLeaseExpiresAt());
        copy.setWorkerHeartbeatAt(source.getWorkerHeartbeatAt());
        copy.setAttemptCount(source.getAttemptCount());
        copy.setMaxAttempts(source.getMaxAttempts());
        copy.setExecutorName(source.getExecutorName());
        copy.setLastError(source.getLastError());
        return copy;
    }
}
