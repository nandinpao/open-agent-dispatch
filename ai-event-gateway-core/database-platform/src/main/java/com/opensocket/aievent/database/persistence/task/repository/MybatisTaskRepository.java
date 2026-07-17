package com.opensocket.aievent.database.persistence.task.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.dao.TaskDao;
import com.opensocket.aievent.database.persistence.task.po.TaskPo;
import com.opensocket.aievent.core.task.TaskExecutionStateTransition;
import com.opensocket.aievent.core.task.TaskLifecycleTransitionGuard;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;
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
            dao.upsert(converter.toPo(task));
            return task;
        }

    @Override
        public TaskRecord saveNewOrGetOpen(TaskRecord task) {
            int inserted = dao.insert(converter.toPo(task));
            if (inserted > 0) {
                return task;
            }
            return findOpenByIncidentAndType(task.getIncidentId(), task.getTaskType())
                    .orElseThrow(() -> new IllegalStateException("Open task insert was skipped but no existing open task was found for incidentId="
                            + task.getIncidentId() + ", taskType=" + task.getTaskType()));
        }

    @Override
        public Optional<TaskRecord> findById(String taskId) {
            return Optional.ofNullable(dao.findById(taskId)).map(converter::toTask);
        }

    @Override
        public Optional<TaskRecord> findOpenByIncidentAndType(String incidentId, TaskType taskType) {
            return Optional.ofNullable(dao.findOpenByIncidentAndType(incidentId, taskType == null ? null : taskType.name())).map(converter::toTask);
        }

    @Override
        public List<TaskRecord> findByIncidentId(String incidentId, int limit) {
            return dao.findByIncidentId(incidentId, cap(limit)).stream().map(converter::toTask).toList();
        }

    @Override
        public List<TaskRecord> search(TaskQuery query) {
            return dao.search(query.getIncidentId(), query.getTenantId(), query.getSiteId(), query.getPlantId(),
                    query.getTaskType() == null ? null : query.getTaskType().name(), query.getStatus() == null ? null : query.getStatus().name(), query.getLimit())
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

    private int cap(int limit) { return Math.max(1, Math.min(limit, 1000)); }
}
