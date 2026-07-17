package com.opensocket.aievent.core.integration;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "core.integration-events", name = "store", havingValue = "MEMORY")
public class InMemoryIntegrationEventRepository implements IntegrationEventRepository {
    private final Map<String, IntegrationEventRecord> records = new ConcurrentHashMap<>();
    private final Map<String, String> eventIds = new ConcurrentHashMap<>();

    @Override
    public synchronized IntegrationEventRecord save(IntegrationEventRecord event) {
        String existing = eventIds.putIfAbsent(event.getEventId(), event.getIntegrationEventId());
        if (existing != null) {
            return records.get(existing);
        }
        records.put(event.getIntegrationEventId(), event);
        return event;
    }

    @Override
    public synchronized List<IntegrationEventRecord> claimDispatchable(ClaimRequest request) {
        List<IntegrationEventRecord> selected = records.values().stream()
                .filter(record -> claimable(record, request.now()))
                .filter(record -> record.getNextAttemptAt() == null || !record.getNextAttemptAt().isAfter(request.now()))
                .sorted(Comparator.comparing(IntegrationEventRecord::getCreatedAt))
                .limit(request.limit())
                .toList();
        selected.forEach(record -> {
            record.setStatus(IntegrationEventStatus.DELIVERING);
            record.setClaimedBy(request.workerId());
            record.setClaimUntil(request.claimUntil());
            record.setUpdatedAt(request.now());
        });
        return new ArrayList<>(selected);
    }

    @Override
    public synchronized PersistenceWriteResult markDelivered(
            String id,
            ClaimOwnership ownership,
            OffsetDateTime deliveredAt) {
        IntegrationEventRecord record = records.get(id);
        if (record == null) {
            return PersistenceWriteResult.notFound(id);
        }
        if (!owns(record, ownership)) {
            return PersistenceWriteResult.ownershipLost(id);
        }
        record.setStatus(IntegrationEventStatus.DELIVERED);
        record.setDeliveredAt(deliveredAt);
        record.setLastError(null);
        record.setNextAttemptAt(null);
        record.setUpdatedAt(deliveredAt);
        clearClaim(record);
        return PersistenceWriteResult.applied(id, 1);
    }

    @Override
    public synchronized PersistenceWriteResult markRetry(
            String id,
            ClaimOwnership ownership,
            int attempt,
            OffsetDateTime nextAttemptAt,
            String error,
            OffsetDateTime updatedAt) {
        IntegrationEventRecord record = records.get(id);
        if (record == null) {
            return PersistenceWriteResult.notFound(id);
        }
        if (!owns(record, ownership)) {
            return PersistenceWriteResult.ownershipLost(id);
        }
        record.setStatus(IntegrationEventStatus.RETRY_WAITING);
        record.setAttemptCount(attempt);
        record.setNextAttemptAt(nextAttemptAt);
        record.setLastError(error);
        record.setUpdatedAt(updatedAt);
        clearClaim(record);
        return PersistenceWriteResult.applied(id, 1);
    }

    @Override
    public synchronized PersistenceWriteResult markDeadLetter(
            String id,
            ClaimOwnership ownership,
            int attempt,
            String error,
            OffsetDateTime updatedAt) {
        IntegrationEventRecord record = records.get(id);
        if (record == null) {
            return PersistenceWriteResult.notFound(id);
        }
        if (!owns(record, ownership)) {
            return PersistenceWriteResult.ownershipLost(id);
        }
        record.setStatus(IntegrationEventStatus.DEAD_LETTER);
        record.setAttemptCount(attempt);
        record.setLastError(error);
        record.setNextAttemptAt(null);
        record.setUpdatedAt(updatedAt);
        clearClaim(record);
        return PersistenceWriteResult.applied(id, 1);
    }

    @Override
    public Map<String, Integer> statusCounts(int limit) {
        Map<String, Integer> result = new LinkedHashMap<>();
        records.values().stream()
                .limit(Math.max(1, limit))
                .forEach(record -> result.merge(record.getStatus().name(), 1, Integer::sum));
        return result;
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private boolean claimable(IntegrationEventRecord record, OffsetDateTime now) {
        return record.getStatus() == IntegrationEventStatus.PENDING
                || record.getStatus() == IntegrationEventStatus.RETRY_WAITING
                || (record.getStatus() == IntegrationEventStatus.DELIVERING
                    && (record.getClaimUntil() == null || !record.getClaimUntil().isAfter(now)));
    }

    private boolean owns(IntegrationEventRecord record, ClaimOwnership ownership) {
        return record.getStatus() == IntegrationEventStatus.DELIVERING
                && ownership.workerId().equals(record.getClaimedBy())
                && ownership.claimUntil().equals(record.getClaimUntil());
    }

    private void clearClaim(IntegrationEventRecord record) {
        record.setClaimedBy(null);
        record.setClaimUntil(null);
    }
}
