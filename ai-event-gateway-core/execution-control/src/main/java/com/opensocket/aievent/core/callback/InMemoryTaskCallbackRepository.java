package com.opensocket.aievent.core.callback;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "task.callback", name = "store", havingValue = "MEMORY")
public class InMemoryTaskCallbackRepository implements TaskCallbackRepository {
    private final ConcurrentMap<String, TaskCallbackRecord> callbacks = new ConcurrentHashMap<>();

    @Override
    public TaskCallbackRecord save(TaskCallbackRecord record) {
        callbacks.put(record.getCallbackId(), record);
        return record;
    }

    @Override
    public boolean tryReserve(TaskCallbackRecord record) {
        return callbacks.putIfAbsent(record.getCallbackId(), record) == null;
    }

    @Override
    public Optional<TaskCallbackRecord> findByCallbackId(String callbackId) {
        return Optional.ofNullable(callbacks.get(callbackId));
    }

    @Override
    public List<TaskCallbackRecord> findByTaskId(String taskId, int limit) {
        return callbacks.values().stream()
                .filter(c -> taskId.equals(c.getTaskId()))
                .sorted(Comparator.comparing(TaskCallbackRecord::getProcessedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }


    @Override
    public List<TaskCallbackRecord> findByDispatchRequestId(String dispatchRequestId, int limit) {
        return callbacks.values().stream()
                .filter(c -> dispatchRequestId.equals(c.getDispatchRequestId()))
                .sorted(Comparator.comparing(TaskCallbackRecord::getProcessedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public List<TaskCallbackRecord> recent(int limit) {
        return callbacks.values().stream()
                .sorted(Comparator.comparing(TaskCallbackRecord::getProcessedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public String mode() { return "MEMORY"; }
}
