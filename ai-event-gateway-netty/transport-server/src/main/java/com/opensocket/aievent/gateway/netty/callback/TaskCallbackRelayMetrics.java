package com.opensocket.aievent.gateway.netty.callback;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-memory metrics for Netty -> Core task callback relay submissions.
 *
 * <p>The relay is asynchronous; these counters describe gateway submission/validation outcomes,
 * not Core task-state acceptance. Core remains the persisted callback/state authority.</p>
 */
@Component
public class TaskCallbackRelayMetrics {
    private static final int MAX_HISTORY = 500;

    private final AtomicLong total = new AtomicLong();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong disabled = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final ArrayDeque<TaskCallbackRelayAttemptRecord> history = new ArrayDeque<>();

    public void record(TaskCallbackRelayResult result) {
        TaskCallbackRelayAttemptRecord record = TaskCallbackRelayAttemptRecord.from(result);
        total.incrementAndGet();
        if (record.submitted()) {
            submitted.incrementAndGet();
        } else if ("RELAY_DISABLED".equals(record.status())) {
            disabled.incrementAndGet();
        } else if ("RELAY_SKIPPED".equals(record.status())) {
            skipped.incrementAndGet();
        } else if (record.status() != null && record.status().contains("REJECT")) {
            rejected.incrementAndGet();
        } else {
            failed.incrementAndGet();
        }
        synchronized (history) {
            history.addFirst(record);
            while (history.size() > MAX_HISTORY) {
                history.removeLast();
            }
        }
    }

    public TaskCallbackRelayMetricsSnapshot snapshot() {
        return new TaskCallbackRelayMetricsSnapshot(total.get(), submitted.get(), failed.get(), disabled.get(), skipped.get(), rejected.get(), OffsetDateTime.now());
    }

    public TaskCallbackRelayMetricsHistory history(int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, MAX_HISTORY));
        List<TaskCallbackRelayAttemptRecord> records = new ArrayList<>(safeLimit);
        synchronized (history) {
            int count = 0;
            for (TaskCallbackRelayAttemptRecord record : history) {
                if (count++ >= safeLimit) break;
                records.add(record);
            }
        }
        return new TaskCallbackRelayMetricsHistory(records, aggregateByStatus(records), OffsetDateTime.now());
    }

    private Map<String, Long> aggregateByStatus(List<TaskCallbackRelayAttemptRecord> records) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TaskCallbackRelayAttemptRecord record : records) {
            String key = record.status() == null ? "UNKNOWN" : record.status();
            counts.put(key, counts.getOrDefault(key, 0L) + 1L);
        }
        return Map.copyOf(counts);
    }
}
