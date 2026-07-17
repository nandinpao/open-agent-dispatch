package com.opensocket.aievent.core.assignment;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "assignment", name = "store", havingValue = "MEMORY")
public class InMemoryTaskDispatchAttemptRepository implements TaskDispatchAttemptRepository {
    private final ConcurrentHashMap<String, TaskDispatchAttempt> attempts = new ConcurrentHashMap<>();

    @Override
    public TaskDispatchAttempt save(TaskDispatchAttempt attempt) {
        attempts.put(attempt.getDispatchAttemptId(), attempt);
        return attempt;
    }

    @Override
    public Optional<TaskDispatchAttempt> findById(String dispatchAttemptId) {
        return Optional.ofNullable(attempts.get(dispatchAttemptId));
    }

    @Override
    public List<TaskDispatchAttempt> findByTaskId(String taskId, int limit) {
        return attempts.values().stream()
                .filter(attempt -> taskId.equals(attempt.getTaskId()))
                .sorted(Comparator.comparing(TaskDispatchAttempt::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public List<TaskDispatchAttempt> recent(int limit) {
        return attempts.values().stream()
                .sorted(Comparator.comparing(TaskDispatchAttempt::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public String mode() { return "MEMORY"; }
}
