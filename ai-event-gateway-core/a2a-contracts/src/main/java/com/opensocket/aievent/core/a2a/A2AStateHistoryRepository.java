package com.opensocket.aievent.core.a2a;

import java.util.List;
import java.util.Optional;

public interface A2AStateHistoryRepository {
    A2AStateHistoryEntry save(A2AStateHistoryEntry entry);
    Optional<A2AStateHistoryEntry> findByIdempotencyKey(String tenantId, String idempotencyKey);
    List<A2AStateHistoryEntry> findByRequest(String tenantId, String requestId, int limit);
    String mode();
}
