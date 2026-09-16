package com.opensocket.aievent.core.a2a;

import java.util.Optional;

/** Durable canonical Result idempotency authority; attempts are not idempotency owners. */
public interface A2AResultIdempotencyClaimRepository {
    A2AResultIdempotencyClaim save(A2AResultIdempotencyClaim claim);
    Optional<A2AResultIdempotencyClaim> findByIdempotencyKey(String tenantId, String idempotencyKey);
    String mode();
}
