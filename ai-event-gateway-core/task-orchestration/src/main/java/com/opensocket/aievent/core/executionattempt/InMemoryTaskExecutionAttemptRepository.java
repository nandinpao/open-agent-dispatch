package com.opensocket.aievent.core.executionattempt;

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
public class InMemoryTaskExecutionAttemptRepository implements TaskExecutionAttemptRepository {
    private final ConcurrentHashMap<String, TaskExecutionAttempt> attempts = new ConcurrentHashMap<>();

    @Override
    public TaskExecutionAttempt save(TaskExecutionAttempt attempt) {
        attempts.put(attempt.getExecutionAttemptId(), attempt);
        return attempt;
    }

    @Override
    public Optional<TaskExecutionAttempt> findById(String executionAttemptId) {
        return Optional.ofNullable(attempts.get(executionAttemptId));
    }

    @Override
    public Optional<TaskExecutionAttempt> findCurrentByAssignmentId(String assignmentId) {
        return attempts.values().stream()
                .filter(attempt -> assignmentId.equals(attempt.getAssignmentId()))
                .filter(attempt -> attempt.getStatus() == TaskExecutionAttemptStatus.CREATED
                        || attempt.getStatus() == TaskExecutionAttemptStatus.RUNNING)
                .max(Comparator.comparing(TaskExecutionAttempt::getCreatedAt));
    }

    @Override
    public List<TaskExecutionAttempt> findByTaskId(String taskId, int limit) {
        return attempts.values().stream()
                .filter(attempt -> taskId.equals(attempt.getTaskId()))
                .sorted(Comparator.comparing(TaskExecutionAttempt::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public int countByTaskId(String taskId) {
        return (int) attempts.values().stream()
                .filter(attempt -> taskId.equals(attempt.getTaskId()))
                .count();
    }

    @Override
    public String mode() { return "MEMORY"; }
}
