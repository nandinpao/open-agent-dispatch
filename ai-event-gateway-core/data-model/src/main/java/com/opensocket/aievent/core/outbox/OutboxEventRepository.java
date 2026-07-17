package com.opensocket.aievent.core.outbox;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;

public interface OutboxEventRepository {
    OutboxEventRecord save(OutboxEventRecord event);
    Optional<OutboxEventRecord> findById(String outboxId);
    Optional<OutboxEventRecord> findByEventId(String eventId);
    List<OutboxEventRecord> claimDispatchable(ClaimRequest claimRequest);
    PersistenceWriteResult markPublished(String outboxId, ClaimOwnership ownership, OffsetDateTime publishedAt);
    PersistenceWriteResult markRetry(String outboxId, ClaimOwnership ownership, int attemptCount,
                                     OffsetDateTime nextAttemptAt, String error, OffsetDateTime updatedAt);
    PersistenceWriteResult markDeadLetter(String outboxId, ClaimOwnership ownership, int attemptCount,
                                          String error, OffsetDateTime updatedAt);
    List<OutboxEventRecord> recent(int limit);
    String mode();
}
