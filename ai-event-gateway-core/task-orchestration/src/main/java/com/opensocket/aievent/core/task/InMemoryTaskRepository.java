package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MEMORY")
public class InMemoryTaskRepository implements TaskRepository {
    private final ConcurrentHashMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    @Override
    public synchronized TaskRecord save(TaskRecord task) {
        tasks.put(task.getTaskId(), task);
        return task;
    }

    @Override
    public synchronized TaskRecord saveNewOrGetOpen(TaskRecord task) {
        Optional<TaskRecord> existing = findOpenByIncidentAndType(task.getIncidentId(), task.getTaskType());
        if (existing.isPresent()) {
            return existing.get();
        }
        return save(task);
    }

    @Override
    public Optional<TaskRecord> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public Optional<TaskRecord> findOpenByIncidentAndType(String incidentId, TaskType taskType) {
        return tasks.values().stream()
                .filter(task -> incidentId.equals(task.getIncidentId()))
                .filter(task -> taskType == task.getTaskType())
                .filter(this::isOpen)
                .max(Comparator.comparing(TaskRecord::getCreatedAt));
    }

    @Override
    public List<TaskRecord> findByIncidentId(String incidentId, int limit) {
        return tasks.values().stream()
                .filter(task -> incidentId.equals(task.getIncidentId()))
                .sorted(Comparator.comparing(TaskRecord::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public List<TaskRecord> search(TaskQuery query) {
        return tasks.values().stream()
                .filter(task -> matches(query.getIncidentId(), task.getIncidentId()))
                .filter(task -> matches(query.getTenantId(), task.getTenantId()))
                .filter(task -> matches(query.getSiteId(), task.getSiteId()))
                .filter(task -> matches(query.getPlantId(), task.getPlantId()))
                .filter(task -> query.getTaskType() == null || query.getTaskType() == task.getTaskType())
                .filter(task -> query.getStatus() == null || query.getStatus() == task.getStatus())
                .sorted(Comparator.comparing(TaskRecord::getCreatedAt).reversed())
                .limit(query.getLimit())
                .toList();
    }


    @Override
    public List<TaskRecord> findOpenUpdatedBefore(OffsetDateTime cutoff, int limit) {
        return tasks.values().stream()
                .filter(this::isOpen)
                .filter(task -> task.getUpdatedAt() != null && task.getUpdatedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(TaskRecord::getUpdatedAt))
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public List<TaskRecord> findByStatusUpdatedBefore(TaskStatus status, OffsetDateTime cutoff, int limit) {
        return tasks.values().stream()
                .filter(task -> status == task.getStatus())
                .filter(task -> task.getUpdatedAt() != null && task.getUpdatedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(TaskRecord::getUpdatedAt))
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public synchronized List<TaskRecord> claimDispatchRecoveryDue(String workerId, OffsetDateTime now, OffsetDateTime claimUntil, int limit) {
        if (workerId == null || workerId.isBlank() || now == null || claimUntil == null || !claimUntil.isAfter(now)) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, 1000));
        return tasks.values().stream()
                .filter(task -> task.getStatus() != null && task.getStatus().isDispatchReady())
                .filter(task -> task.getDispatchRecoveryClaimUntil() == null || !task.getDispatchRecoveryClaimUntil().isAfter(now))
                .filter(task -> task.getNextDispatchAttemptAt() != null && !task.getNextDispatchAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(task -> task.getNextDispatchAttemptAt() == null ? task.getUpdatedAt() : task.getNextDispatchAttemptAt(), Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(capped)
                .peek(task -> {
                    task.setDispatchRecoveryClaimedBy(workerId.trim());
                    task.setDispatchRecoveryClaimUntil(claimUntil);
                    task.setUpdatedAt(now);
                })
                .toList();
    }


    @Override
    public synchronized TaskRecord suspendDispatchUntilConfigurationChange(String taskId, String blockerCode, String reason, OffsetDateTime now) {
        TaskRecord task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
        String code = blockerCode == null || blockerCode.isBlank() ? "CONFIGURATION_BLOCKED" : blockerCode.trim();
        task.setStatus(TaskStatus.RETRY_WAIT);
        task.setNextDispatchAttemptAt(null);
        task.setDispatchRetryReason("WAITING_CONFIGURATION:" + code + ":" + (reason == null ? "" : reason.trim()));
        task.setDispatchRecoveryClaimedBy(null);
        task.setDispatchRecoveryClaimUntil(null);
        task.setUpdatedAt(now);
        task.setLifecycleReason("Waiting for dispatch configuration change: " + code);
        return task;
    }

    @Override
    public synchronized int wakeConfigurationBlockedTasks(String tenantId, String sourceSystem, OffsetDateTime now, String reason) {
        int[] count = {0};
        tasks.values().stream()
                .filter(task -> tenantId != null && tenantId.equalsIgnoreCase(String.valueOf(task.getTenantId())))
                .filter(task -> sourceSystem == null || sourceSystem.isBlank() || sourceSystem.equalsIgnoreCase(String.valueOf(task.getSourceSystem())))
                .filter(task -> task.getDispatchRetryReason() != null && task.getDispatchRetryReason().startsWith("WAITING_CONFIGURATION:"))
                .forEach(task -> {
                    task.setStatus(TaskStatus.QUEUED);
                    task.setNextDispatchAttemptAt(now);
                    task.setDispatchRetryReason(reason);
                    task.setDispatchRecoveryClaimedBy(null);
                    task.setDispatchRecoveryClaimUntil(null);
                    task.setUpdatedAt(now);
                    task.setLifecycleReason(reason);
                    count[0]++;
                });
        return count[0];
    }

    @Override
    public synchronized boolean clearDispatchRecoveryClaim(String taskId, String workerId, OffsetDateTime claimUntil, OffsetDateTime now) {
        TaskRecord task = tasks.get(taskId);
        if (task == null || workerId == null || workerId.isBlank()) {
            return false;
        }
        if (!workerId.trim().equals(task.getDispatchRecoveryClaimedBy())) {
            return false;
        }
        if (claimUntil != null && !claimUntil.equals(task.getDispatchRecoveryClaimUntil())) {
            return false;
        }
        task.setDispatchRecoveryClaimedBy(null);
        task.setDispatchRecoveryClaimUntil(null);
        task.setUpdatedAt(now);
        return true;
    }

    @Override
    public synchronized boolean transitionExecutionState(TaskExecutionStateTransition transition) {
        TaskRecord task = transition == null ? null : tasks.get(transition.getTaskId());
        if (task == null) {
            return false;
        }
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
        tasks.put(task.getTaskId(), task);
        return true;
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private boolean isOpen(TaskRecord task) {
        return task.getStatus() == null || !task.getStatus().isTerminal();
    }
}
