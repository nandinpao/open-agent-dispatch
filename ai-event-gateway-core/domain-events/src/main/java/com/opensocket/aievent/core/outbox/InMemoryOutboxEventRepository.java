package com.opensocket.aievent.core.outbox;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "core.outbox", name = "store", havingValue = "MEMORY")
public class InMemoryOutboxEventRepository implements OutboxEventRepository {
    private final ConcurrentHashMap<String, OutboxEventRecord> data = new ConcurrentHashMap<>();

    @Override
    public synchronized OutboxEventRecord save(OutboxEventRecord event) {
        data.values().stream()
                .filter(existing -> event.getEventId().equals(existing.getEventId()))
                .findFirst()
                .ifPresent(existing -> event.setOutboxId(existing.getOutboxId()));
        data.putIfAbsent(event.getOutboxId(), event);
        return data.get(event.getOutboxId());
    }

    @Override
    public Optional<OutboxEventRecord> findById(String id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public Optional<OutboxEventRecord> findByEventId(String id) {
        return data.values().stream().filter(event -> id.equals(event.getEventId())).findFirst();
    }

    @Override
    public synchronized List<OutboxEventRecord> claimDispatchable(ClaimRequest request) {
        List<OutboxEventRecord> rows = data.values().stream()
                .filter(event -> claimable(event, request.now()))
                .sorted(Comparator.comparing(OutboxEventRecord::getCreatedAt))
                .limit(request.limit())
                .toList();
        for (OutboxEventRecord event : rows) {
            event.setStatus(OutboxEventStatus.DISPATCHING);
            event.setClaimedBy(request.workerId());
            event.setClaimUntil(request.claimUntil());
            event.setUpdatedAt(request.now());
        }
        return new ArrayList<>(rows);
    }

    @Override
    public synchronized PersistenceWriteResult markPublished(
            String id,
            ClaimOwnership ownership,
            OffsetDateTime publishedAt) {
        OutboxEventRecord event = data.get(id);
        if (event == null) {
            return PersistenceWriteResult.notFound(id);
        }
        if (!owns(event, ownership)) {
            return PersistenceWriteResult.ownershipLost(id);
        }
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(publishedAt);
        event.setUpdatedAt(publishedAt);
        event.setLastError(null);
        event.setNextAttemptAt(null);
        clearClaim(event);
        return PersistenceWriteResult.applied(id, 1);
    }

    @Override
    public synchronized PersistenceWriteResult markRetry(
            String id,
            ClaimOwnership ownership,
            int attempts,
            OffsetDateTime nextAttemptAt,
            String error,
            OffsetDateTime updatedAt) {
        OutboxEventRecord event = data.get(id);
        if (event == null) {
            return PersistenceWriteResult.notFound(id);
        }
        if (!owns(event, ownership)) {
            return PersistenceWriteResult.ownershipLost(id);
        }
        event.setStatus(OutboxEventStatus.RETRY_WAITING);
        event.setAttemptCount(attempts);
        event.setNextAttemptAt(nextAttemptAt);
        event.setLastError(error);
        event.setUpdatedAt(updatedAt);
        clearClaim(event);
        return PersistenceWriteResult.applied(id, 1);
    }

    @Override
    public synchronized PersistenceWriteResult markDeadLetter(
            String id,
            ClaimOwnership ownership,
            int attempts,
            String error,
            OffsetDateTime updatedAt) {
        OutboxEventRecord event = data.get(id);
        if (event == null) {
            return PersistenceWriteResult.notFound(id);
        }
        if (!owns(event, ownership)) {
            return PersistenceWriteResult.ownershipLost(id);
        }
        event.setStatus(OutboxEventStatus.DEAD_LETTER);
        event.setAttemptCount(attempts);
        event.setLastError(error);
        event.setNextAttemptAt(null);
        event.setUpdatedAt(updatedAt);
        clearClaim(event);
        return PersistenceWriteResult.applied(id, 1);
    }

    @Override
    public List<OutboxEventRecord> recent(int limit) {
        List<OutboxEventRecord> result = new ArrayList<>(data.values());
        result.sort(Comparator.comparing(OutboxEventRecord::getCreatedAt).reversed());
        return result.stream().limit(Math.max(1, Math.min(limit, 1000))).toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private boolean claimable(OutboxEventRecord event, OffsetDateTime now) {
        if (event.getStatus() == OutboxEventStatus.PENDING) {
            return true;
        }
        if (event.getStatus() == OutboxEventStatus.RETRY_WAITING) {
            return event.getNextAttemptAt() == null || !event.getNextAttemptAt().isAfter(now);
        }
        return event.getStatus() == OutboxEventStatus.DISPATCHING
                && (event.getClaimUntil() == null || !event.getClaimUntil().isAfter(now));
    }

    private boolean owns(OutboxEventRecord event, ClaimOwnership ownership) {
        return event.getStatus() == OutboxEventStatus.DISPATCHING
                && ownership.workerId().equals(event.getClaimedBy())
                && ownership.claimUntil().equals(event.getClaimUntil());
    }

    private void clearClaim(OutboxEventRecord event) {
        event.setClaimedBy(null);
        event.setClaimUntil(null);
    }
}
