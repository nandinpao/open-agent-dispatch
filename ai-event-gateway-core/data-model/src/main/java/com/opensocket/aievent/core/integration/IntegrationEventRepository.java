package com.opensocket.aievent.core.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;

public interface IntegrationEventRepository {
    IntegrationEventRecord save(IntegrationEventRecord event);
    List<IntegrationEventRecord> claimDispatchable(ClaimRequest claimRequest);
    PersistenceWriteResult markDelivered(String integrationEventId, ClaimOwnership ownership, OffsetDateTime deliveredAt);
    PersistenceWriteResult markRetry(String integrationEventId, ClaimOwnership ownership, int attemptCount,
                                     OffsetDateTime nextAttemptAt, String error, OffsetDateTime updatedAt);
    PersistenceWriteResult markDeadLetter(String integrationEventId, ClaimOwnership ownership, int attemptCount,
                                          String error, OffsetDateTime updatedAt);
    Map<String, Integer> statusCounts(int limit);
    String mode();
}
