package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface A2AIdempotencyRepository {
    A2AIdempotencyClaim claim(String tenantId, String idempotencyKey, String operationType,
                               String requestHash, OffsetDateTime createdAt, OffsetDateTime expiresAt);
    Optional<A2AIdempotencyRecord> find(String tenantId, String idempotencyKey, String operationType);
    void complete(String tenantId, String idempotencyKey, String operationType,
                  String resourceType, String resourceId, OffsetDateTime completedAt);
    String mode();
}
