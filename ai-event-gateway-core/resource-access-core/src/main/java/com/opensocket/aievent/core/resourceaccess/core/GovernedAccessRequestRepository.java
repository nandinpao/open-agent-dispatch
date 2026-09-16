package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.GovernedAccessRequestRecord;
import com.opensocket.aievent.core.resourceaccess.contract.GovernedAccessRequestState;
import java.time.Instant;
import java.util.Optional;

/** Persistence port for request metadata. ScopeGrantRecord remains the canonical grant policy state. */
public interface GovernedAccessRequestRepository {
    Optional<GovernedAccessRequestRecord> find(String tenantId, String requestId);
    Optional<GovernedAccessRequestRecord> findByIdempotencyKey(String tenantId, String idempotencyKey);
    GovernedAccessRequestRecord insert(GovernedAccessRequestRecord request, String correlationId);
    GovernedAccessRequestRecord transition(GovernedAccessRequestRecord current,
                                            GovernedAccessRequestState targetState,
                                            String approvedBy,
                                            String actorId,
                                            String reason,
                                            String correlationId,
                                            String idempotencyKey,
                                            Instant changedAt);
}
