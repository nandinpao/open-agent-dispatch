package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    TaskRecord save(TaskRecord task);
    /**
     * Creates a task unless another non-terminal task already exists for the same incident/type.
     * Implementations must make this operation concurrency-safe.
     */
    default TaskRecord saveNewOrGetOpen(TaskRecord task) {
        return save(task);
    }
    Optional<TaskRecord> findById(String taskId);
    Optional<TaskRecord> findOpenByIncidentAndType(String incidentId, TaskType taskType);
    List<TaskRecord> findByIncidentId(String incidentId, int limit);
    List<TaskRecord> search(TaskQuery query);
    default List<TaskRecord> findOpenUpdatedBefore(OffsetDateTime cutoff, int limit) {
        return List.of();
    }
    default List<TaskRecord> findByStatusUpdatedBefore(TaskStatus status, OffsetDateTime cutoff, int limit) {
        return List.of();
    }

    default List<TaskRecord> claimDispatchRecoveryDue(String workerId, OffsetDateTime now, OffsetDateTime claimUntil, int limit) {
        return List.of();
    }

    default boolean clearDispatchRecoveryClaim(String taskId, String workerId, OffsetDateTime claimUntil, OffsetDateTime now) {
        return false;
    }

    default TaskRecord deferDispatchAttempt(String taskId, OffsetDateTime nextAttemptAt, int attemptCount, String reason, OffsetDateTime now) {
        TaskRecord task = findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        task.setStatus(TaskStatus.RETRY_WAIT);
        task.setNextDispatchAttemptAt(nextAttemptAt);
        task.setDispatchAttemptCount(attemptCount);
        task.setDispatchRetryReason(reason);
        task.setDispatchRecoveryClaimedBy(null);
        task.setDispatchRecoveryClaimUntil(null);
        task.setUpdatedAt(now);
        task.setLifecycleReason(reason);
        return save(task);
    }

    default TaskRecord clearDispatchDelay(String taskId, OffsetDateTime now, String reason) {
        TaskRecord task = findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        task.setNextDispatchAttemptAt(null);
        task.setDispatchRetryReason(null);
        task.setDispatchRecoveryClaimedBy(null);
        task.setDispatchRecoveryClaimUntil(null);
        task.setUpdatedAt(now);
        task.setLifecycleReason(reason);
        return save(task);
    }

    default TaskRecord suspendDispatchUntilConfigurationChange(String taskId, String blockerCode, String reason, OffsetDateTime now) {
        TaskRecord task = findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        String code = blockerCode == null || blockerCode.isBlank() ? "CONFIGURATION_BLOCKED" : blockerCode.trim();
        String message = reason == null || reason.isBlank() ? "Dispatch configuration must be repaired before retry" : reason.trim();
        task.setStatus(TaskStatus.RETRY_WAIT);
        task.setNextDispatchAttemptAt(null);
        task.setDispatchRetryReason("WAITING_CONFIGURATION:" + code + ":" + message);
        task.setDispatchRecoveryClaimedBy(null);
        task.setDispatchRecoveryClaimUntil(null);
        task.setUpdatedAt(now);
        task.setLifecycleReason("Waiting for dispatch configuration change: " + code);
        return save(task);
    }

    default int wakeConfigurationBlockedTasks(String tenantId, String sourceSystem, OffsetDateTime now, String reason) {
        return 0;
    }

    default boolean transitionExecutionState(TaskExecutionStateTransition transition) {
        if (transition == null || transition.getTaskId() == null || transition.getTaskId().isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        Optional<TaskRecord> current = findById(transition.getTaskId());
        if (current.isEmpty()) {
            return false;
        }
        TaskRecord task = current.get();
        if (transition.getAllowedCurrentStatuses().isEmpty()
                || !transition.getAllowedCurrentStatuses().contains(task.getStatus())) {
            return false;
        }
        if (!TaskLifecycleTransitionGuard.canTransition(task.getStatus(), transition.getNewStatus())) {
            return false;
        }
        task.setStatus(transition.getNewStatus());
        task.setTimeoutAt(transition.getTimeoutAt());
        task.setTerminalAt(transition.getTerminalAt());
        task.setUpdatedAt(transition.getUpdatedAt());
        task.setLifecycleReason(transition.getLifecycleReason());
        save(task);
        return true;
    }

    String mode();
}
