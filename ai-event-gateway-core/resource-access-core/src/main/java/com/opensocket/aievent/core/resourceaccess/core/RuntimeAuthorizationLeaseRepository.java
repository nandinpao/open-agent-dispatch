package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.Optional;

/** Persistence authority for expiring Runtime Authorization leases and immutable lease events. */
public interface RuntimeAuthorizationLeaseRepository {
    Optional<RuntimeAuthorizationLease> find(String tenantId, String leaseId);
    /** Atomically allocates the next resource fencing version and persists the lease in one transaction. */
    RuntimeAuthorizationLease insert(RuntimeAuthorizationLease leaseDraft, String correlationId);
    RuntimeAuthorizationLease update(
            RuntimeAuthorizationLease current,
            RuntimeLeaseStatus target,
            SecurityEpoch epoch,
            Instant maximumStaleUntil,
            Instant recheckAfter,
            String reasonCode,
            String correlationId,
            Instant at);
    void recordCheckpoint(
            RuntimeAuthorizationLease current,
            OperationPhase operationPhase,
            String reasonCode,
            String correlationId,
            Instant at);
}
