package com.opensocket.aievent.database.persistence.task.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.dao.TaskDao;
import com.opensocket.aievent.database.persistence.task.po.TaskPo;
import com.opensocket.aievent.core.task.TaskExecutionStateTransition;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan;
import com.opensocket.aievent.core.task.TaskLifecycleTransitionGuard;

import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;
import com.opensocket.aievent.core.task.domain.TaskStateTransitionCommand;
import com.opensocket.aievent.database.persistence.task.converter.TaskPersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
public class MybatisTaskRepository implements TaskRepository {
    private final TaskDao dao;
    private final TaskPersistenceConverter converter;

    public MybatisTaskRepository(TaskDao dao, TaskPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
        public TaskRecord save(TaskRecord task) {
            normalizeAggregate(task);
            dao.upsert(converter.toPo(task));
            return findByTenantAndId(task.getTenantId(), task.getTaskId()).orElse(task);
        }

    @Override
        public TaskRecord saveNewOrGetOpen(TaskRecord task) {
            normalizeAggregate(task);
            int inserted = dao.insert(converter.toPo(task));
            if (inserted > 0) {
                return findByTenantAndId(task.getTenantId(), task.getTaskId()).orElse(task);
            }
            return findOpenByTenantAndIncidentAndType(task.getTenantId(), task.getIncidentId(), task.getTaskType())
                    .orElseThrow(() -> new IllegalStateException("Open task insert was skipped but no existing open task was found for tenantId="
                            + task.getTenantId() + ", incidentId=" + task.getIncidentId() + ", taskType=" + task.getTaskType()));
        }

    @Override
        public Optional<TaskRecord> findById(String taskId) {
            return Optional.ofNullable(dao.findById(taskId)).map(converter::toTask);
        }

    @Override
        public Optional<TaskRecord> findByTenantAndId(String tenantId, String taskId) {
            return Optional.ofNullable(dao.findByTenantAndId(tenantId, taskId)).map(converter::toTask);
        }

    @Override
        public List<TaskRecord> findByRootTaskId(String tenantId, String rootTaskId, int limit) {
            return dao.findByRootTaskId(tenantId, rootTaskId, cap(limit)).stream().map(converter::toTask).toList();
        }

    @Override
        public List<TaskRecord> findByParentTaskId(String tenantId, String parentTaskId, int limit) {
            return dao.findByParentTaskId(tenantId, parentTaskId, cap(limit)).stream().map(converter::toTask).toList();
        }

    @Override
    public Optional<TaskRecord> transferOwnership(String tenantId, String taskId, long expectedVersion,
            String ownerDepartmentId, String ownerGroupId, String actorId, String reason,
            String correlationId, OffsetDateTime changedAt) {
        int rows = dao.transferOwnership(tenantId, taskId, expectedVersion,
                ownerDepartmentId == null || ownerDepartmentId.isBlank() ? "UNASSIGNED" : ownerDepartmentId.trim(),
                ownerGroupId == null || ownerGroupId.isBlank() ? null : ownerGroupId.trim(),
                actorId, reason, correlationId, changedAt);
        return rows == 0 ? Optional.empty() : findByTenantAndId(tenantId, taskId);
    }

    @Override
        public Optional<TaskRecord> transitionGovernanceState(TaskStateTransitionCommand command) {
            if (command == null || command.newStatus() == null) return Optional.empty();
            Optional<TaskRecord> current = findByTenantAndId(command.tenantId(), command.taskId());
            if (current.isEmpty() || current.get().getVersion() != command.expectedVersion()) return Optional.empty();
            if (!TaskLifecycleTransitionGuard.canTransition(current.get().getStatus(), command.newStatus())) return Optional.empty();
            int rows = dao.transitionGovernanceState(
                    command.tenantId(), command.taskId(), command.expectedVersion(), command.newStatus().name(),
                    command.reasonCode(), command.reason(), command.actorType().name(), command.actorId(),
                    command.correlationId(), command.idempotencyKey(), command.transitionAt());
            return rows == 0 ? Optional.empty() : findByTenantAndId(command.tenantId(), command.taskId());
        }

    @Override
        public Optional<TaskRecord> findOpenByIncidentAndType(String incidentId, TaskType taskType) {
            return Optional.ofNullable(dao.findOpenByIncidentAndType(incidentId, taskType == null ? null : taskType.name())).map(converter::toTask);
        }

    @Override
        public Optional<TaskRecord> findOpenByTenantAndIncidentAndType(String tenantId, String incidentId, TaskType taskType) {
            return Optional.ofNullable(dao.findOpenByTenantAndIncidentAndType(tenantId, incidentId,
                    taskType == null ? null : taskType.name())).map(converter::toTask);
        }

    @Override
        public List<TaskRecord> findByIncidentId(String incidentId, int limit) {
            return dao.findByIncidentId(incidentId, cap(limit)).stream().map(converter::toTask).toList();
        }

    @Override
        public List<TaskRecord> findByTenantAndIncidentId(String tenantId, String incidentId, int limit) {
            return dao.findByTenantAndIncidentId(tenantId, incidentId, cap(limit)).stream().map(converter::toTask).toList();
        }

    @Override
        public List<TaskRecord> search(TaskQuery query) {
            return dao.search(query.getIncidentId(), query.getTenantId(), query.getSiteId(), query.getPlantId(),
                    query.getTaskType() == null ? null : query.getTaskType().name(), query.getStatus() == null ? null : query.getStatus().name(),
                    query.getBeforeCreatedAt(), query.getBeforeTaskId(), query.getLimit())
                    .stream().map(converter::toTask).toList();
        }

    @Override
    public List<TaskRecord> searchAuthorized(TaskQuery query, TaskScopeQueryPlan plan) {
        if (plan == null || plan.denyAll()) return List.of();
        return dao.searchAuthorized(query.getIncidentId(), plan.tenantId(), query.getSiteId(), query.getPlantId(),
                query.getTaskType() == null ? null : query.getTaskType().name(),
                query.getStatus() == null ? null : query.getStatus().name(), query.getBeforeCreatedAt(),
                query.getBeforeTaskId(), query.getLimit(), plan)
                .stream().map(converter::toTask).toList();
    }


    @Override
    public List<TaskRecord> searchAuthorizedTarget(TaskQuery query, TaskScopeQueryPlan plan) {
        if (plan == null || plan.denyAll()) return List.of();
        return dao.searchAuthorizedTarget(query.getIncidentId(), plan.tenantId(), query.getSiteId(), query.getPlantId(),
                query.getTaskType() == null ? null : query.getTaskType().name(),
                query.getStatus() == null ? null : query.getStatus().name(), query.getBeforeCreatedAt(),
                query.getBeforeTaskId(), query.getLimit(), plan)
                .stream().map(converter::toTask).toList();
    }

    @Override
        public List<TaskRecord> findOpenUpdatedBefore(OffsetDateTime cutoff, int limit) {
            return dao.findOpenUpdatedBefore(cutoff, cap(limit)).stream().map(converter::toTask).toList();
        }

    @Override
        public List<TaskRecord> findByStatusUpdatedBefore(TaskStatus status, OffsetDateTime cutoff, int limit) {
            return dao.findByStatusUpdatedBefore(status == null ? null : status.name(), cutoff, cap(limit)).stream().map(converter::toTask).toList();
        }

    @Override
        public List<TaskRecord> claimDispatchRecoveryDue(String workerId, OffsetDateTime now, OffsetDateTime claimUntil, int limit) {
            return dao.claimDispatchRecoveryDue(workerId, now, claimUntil, cap(limit)).stream().map(converter::toTask).toList();
        }

    @Override
        public TaskRecord suspendDispatchUntilConfigurationChange(String taskId, String blockerCode, String reason, OffsetDateTime now) {
            int rows = dao.suspendDispatchUntilConfigurationChange(taskId, blockerCode, reason, now);
            if (rows == 0) throw new IllegalArgumentException("Task not found: " + taskId);
            return findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found after suspend: " + taskId));
        }

    @Override
        public int wakeConfigurationBlockedTasks(String tenantId, String sourceSystem, OffsetDateTime now, String reason) {
            return dao.wakeConfigurationBlockedTasks(tenantId, sourceSystem, now, reason);
        }

    @Override
        public boolean clearDispatchRecoveryClaim(String taskId, String workerId, OffsetDateTime claimUntil, OffsetDateTime now) {
            return dao.clearDispatchRecoveryClaim(taskId, workerId, claimUntil, now) > 0;
        }

    @Override
        public boolean transitionExecutionState(TaskExecutionStateTransition transition) {
            Optional<TaskRecord> current = findById(transition.getTaskId());
            if (current.isEmpty()) {
                return false;
            }
            if (transition.getAllowedCurrentStatuses().isEmpty()
                    || !transition.getAllowedCurrentStatuses().contains(current.get().getStatus())) {
                return false;
            }
            if (!TaskLifecycleTransitionGuard.canTransition(current.get().getStatus(), transition.getNewStatus())) {
                return false;
            }
            int rows = dao.transitionExecutionState(
                    transition.getTaskId(),
                    transition.allowedCurrentStatusNames(),
                    transition.newStatusName(),
                    transition.getTimeoutAt(),
                    transition.getTerminalAt(),
                    transition.getUpdatedAt(),
                    transition.getLifecycleReason());
            return rows > 0;
        }

    @Override
        public String mode() { return "MYBATIS"; }

    private void normalizeAggregate(TaskRecord task) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            throw new IllegalArgumentException("Task with taskId is required");
        }
        if (task.getTaskKey() == null) task.setTaskKey(task.getTaskId());
        if (task.getTitle() == null) task.setTitle((task.getEffectiveTaskTypeCode() == null ? "Task" : task.getEffectiveTaskTypeCode()) + " " + task.getTaskId());
        if (task.getRootTaskId() == null) {
            if (task.getParentTaskId() == null) {
                task.setRootTaskId(task.getTaskId());
            } else if (task.getTenantId() != null) {
                TaskPo parent = dao.findByTenantAndId(task.getTenantId(), task.getParentTaskId());
                if (parent != null) task.setRootTaskId(parent.getRootTaskId() == null ? parent.getTaskId() : parent.getRootTaskId());
            }
        }
        if (task.getVersion() < 1L) task.setVersion(1L);
        task.ensureCreationProvenanceDefaults();
    }

    private int cap(int limit) { return Math.max(1, Math.min(limit, 1000)); }
}
