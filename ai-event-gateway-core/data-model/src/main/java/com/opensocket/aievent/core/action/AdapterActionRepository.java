package com.opensocket.aievent.core.action;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.LeaseRenewalRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;

public interface AdapterActionRepository {
    AdapterAction save(AdapterAction action);

    default AdapterAction saveNewOrGetByIdempotencyKey(AdapterAction action) {
        return save(action);
    }

    Optional<AdapterAction> findById(String actionId);
    Optional<AdapterAction> findByIdempotencyKey(String idempotencyKey);
    List<AdapterAction> findByIncidentId(String incidentId, int limit);
    List<AdapterAction> findByTaskId(String taskId, int limit);
    List<AdapterAction> findByStatus(AdapterActionStatus status, int limit);
    List<AdapterAction> findExecutablePending(OffsetDateTime now, int limit);

    default Optional<AdapterAction> claimNext(AdapterType adapterType, ClaimRequest request) {
        return Optional.empty();
    }

    default Optional<AdapterAction> extendLease(LeaseRenewalRequest request) {
        return Optional.empty();
    }

    default PersistenceWriteResult saveClaimed(
            AdapterAction action,
            ClaimOwnership ownership,
            OffsetDateTime now) {
        return PersistenceWriteResult.ownershipLost(action == null ? null : action.getActionId());
    }

    default PersistenceWriteResult recoverExpiredClaim(
            AdapterAction action,
            ClaimOwnership ownership,
            OffsetDateTime observedAt) {
        return PersistenceWriteResult.ownershipLost(action == null ? null : action.getActionId());
    }

    List<AdapterAction> recent(int limit);
    String mode();
}
