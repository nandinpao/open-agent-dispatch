package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "dispatch", name = "attempt-history-store", havingValue = "MEMORY")
public class InMemoryDispatchAttemptHistoryRepository implements DispatchAttemptHistoryRepository {
    private final ConcurrentMap<String, DispatchAttemptHistoryRecord> records = new ConcurrentHashMap<>();

    @Override
    public DispatchAttemptHistoryRecord append(DispatchAttemptHistoryRecord record) {
        records.put(record.getHistoryId(), record);
        return record;
    }

    @Override
    public List<DispatchAttemptHistoryRecord> findByTaskId(String taskId, int limit) {
        return records.values().stream()
                .filter(record -> taskId.equals(record.getTaskId()))
                .sorted(Comparator.comparing(
                        DispatchAttemptHistoryRecord::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap(limit))
                .toList();
    }

    @Override
    public List<DispatchAttemptHistoryRecord> findByDispatchRequestId(String dispatchRequestId, int limit) {
        return records.values().stream()
                .filter(record -> dispatchRequestId.equals(record.getDispatchRequestId()))
                .sorted(Comparator.comparing(DispatchAttemptHistoryRecord::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap(limit))
                .toList();
    }

    @Override
    public List<DispatchAttemptHistoryRecord> recent(int limit) {
        return records.values().stream()
                .sorted(Comparator.comparing(
                        DispatchAttemptHistoryRecord::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap(limit))
                .toList();
    }


    @Override
    public List<DispatchAttemptHistoryRecord> findSince(OffsetDateTime since, int limit) {
        OffsetDateTime cutoff = since == null ? OffsetDateTime.MIN : since;
        return records.values().stream()
                .filter(record -> record.getOccurredAt() != null && !record.getOccurredAt().isBefore(cutoff))
                .sorted(Comparator.comparing(
                        DispatchAttemptHistoryRecord::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap(limit))
                .toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 1000));
    }
}
