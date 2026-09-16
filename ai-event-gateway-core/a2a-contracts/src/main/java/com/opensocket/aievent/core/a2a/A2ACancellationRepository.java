package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface A2ACancellationRepository {
    A2ACancellationRecord save(A2ACancellationRecord value);
    A2ACancellationRecord saveExpectedVersion(A2ACancellationRecord value, long expectedVersion);
    Optional<A2ACancellationRecord> findById(String tenantId, String cancellationId);
    Optional<A2ACancellationRecord> findByRequest(String tenantId, String requestId);
    Optional<A2ACancellationRecord> findByIdempotencyKey(String tenantId, String idempotencyKey);
    List<A2ACancellationRecord> findDue(OffsetDateTime now, int limit);
    List<A2ACancellationRecord> findRecent(String tenantId, int limit);
    String mode();
}
