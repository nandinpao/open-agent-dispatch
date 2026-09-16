package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.opensocket.aievent.core.task.domain.TaskStateTransitionCommand;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan;

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
    default Optional<TaskRecord> findByTenantAndId(String tenantId, String taskId) {
        if (tenantId == null || tenantId.isBlank()) return Optional.empty();
        return findById(taskId).filter(task -> tenantId.equals(task.getTenantId()));
    }
    default List<TaskRecord> findByRootTaskId(String tenantId, String rootTaskId, int limit) {
        return List.of();
    }
    default List<TaskRecord> findByParentTaskId(String tenantId, String parentTaskId, int limit) {
        return List.of();
    }
    /** Optimistic ownership mutation owned by Task Domain. */
    default Optional<TaskRecord> transferOwnership(String tenantId, String taskId, long expectedVersion,
            String ownerDepartmentId, String ownerGroupId, String actorId, String reason,
            String correlationId, OffsetDateTime changedAt) {
        Optional<TaskRecord> current = findByTenantAndId(tenantId, taskId);
        if (current.isEmpty() || current.get().getVersion() != expectedVersion) return Optional.empty();
        TaskRecord task = current.get();
        task.setOwnerDepartmentId(ownerDepartmentId);
        task.setOwnerGroupId(ownerGroupId);
        task.setLifecycleReason(reason);
        task.setCorrelationId(correlationId);
        task.setUpdatedAt(changedAt);
        task.setVersion(expectedVersion + 1);
        return Optional.of(save(task));
    }
    default Optional<TaskRecord> transitionGovernanceState(TaskStateTransitionCommand command) {
        if (command == null) return Optional.empty();
        Optional<TaskRecord> current = findByTenantAndId(command.tenantId(), command.taskId());
        if (current.isEmpty() || current.get().getVersion() != command.expectedVersion()) return Optional.empty();
        TaskRecord task = current.get();
        if (!TaskLifecycleTransitionGuard.canTransition(task.getStatus(), command.newStatus())) return Optional.empty();
        task.setStatus(command.newStatus());
        task.setLifecycleReason(command.reason());
        task.setCorrelationId(command.correlationId());
        task.setUpdatedAt(command.transitionAt());
        task.setVersion(task.getVersion() + 1);
        return Optional.of(save(task));
    }
    Optional<TaskRecord> findOpenByIncidentAndType(String incidentId, TaskType taskType);
    default Optional<TaskRecord> findOpenByTenantAndIncidentAndType(String tenantId, String incidentId, TaskType taskType) {
        return findOpenByIncidentAndType(incidentId, taskType)
                .filter(task -> tenantId != null && tenantId.equals(task.getTenantId()));
    }
    List<TaskRecord> findByIncidentId(String incidentId, int limit);
    default List<TaskRecord> findByTenantAndIncidentId(String tenantId, String incidentId, int limit) {
        return findByIncidentId(incidentId, limit).stream()
                .filter(task -> tenantId != null && tenantId.equals(task.getTenantId())).toList();
    }
    List<TaskRecord> search(TaskQuery query);
    /** Scope-aware query path. Database implementations must apply the plan before pagination. */
    default List<TaskRecord> searchAuthorized(TaskQuery query, TaskScopeQueryPlan plan) {
        if (plan == null || plan.denyAll()) return List.of();
        boolean tenantWide = plan.strategy()
                == com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryStrategy.TENANT;
        return search(query).stream()
                .filter(task -> tenantWide
                        || plan.explicitTaskIds().contains(task.getTaskId())
                        || plan.exactDepartmentIds().contains(task.getOwnerDepartmentId())
                        || plan.exactDepartmentIds().contains(task.getRequesterDepartmentId())
                        || plan.exactDepartmentIds().contains(task.getExecutorDepartmentId())
                        || plan.groupIds().contains(task.getOwnerGroupId())
                        || plan.groupIds().contains(task.getRequesterGroupId())
                        || plan.groupIds().contains(task.getExecutorGroupId()))
                .filter(task -> !plan.excludedTaskIds().contains(task.getTaskId()))
                .filter(task -> !plan.deniedDepartmentIds().contains(task.getOwnerDepartmentId())
                        && !plan.deniedDepartmentIds().contains(task.getRequesterDepartmentId())
                        && !plan.deniedDepartmentIds().contains(task.getExecutorDepartmentId()))
                .filter(task -> !plan.deniedGroupIds().contains(task.getOwnerGroupId())
                        && !plan.deniedGroupIds().contains(task.getRequesterGroupId())
                        && !plan.deniedGroupIds().contains(task.getExecutorGroupId()))
                .limit(query == null ? 100 : query.getLimit()).toList();
    }
    /** Phase 6C-2 independent Target read path. Database implementations must filter before pagination. */
    default List<TaskRecord> searchAuthorizedTarget(TaskQuery query, TaskScopeQueryPlan plan) {
        return searchAuthorized(query, plan);
    }

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
