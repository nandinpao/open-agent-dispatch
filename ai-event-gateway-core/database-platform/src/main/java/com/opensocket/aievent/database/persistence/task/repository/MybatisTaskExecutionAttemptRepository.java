package com.opensocket.aievent.database.persistence.task.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.executionattempt.TaskExecutionAttempt;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttemptRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.converter.TaskExecutionAttemptPersistenceConverter;
import com.opensocket.aievent.database.persistence.task.dao.TaskExecutionAttemptDao;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "assignment", name = "store", havingValue = "MYBATIS")
public class MybatisTaskExecutionAttemptRepository implements TaskExecutionAttemptRepository {
    private final TaskExecutionAttemptDao dao;
    private final TaskExecutionAttemptPersistenceConverter converter;

    public MybatisTaskExecutionAttemptRepository(TaskExecutionAttemptDao dao, TaskExecutionAttemptPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public TaskExecutionAttempt save(TaskExecutionAttempt attempt) {
        dao.upsert(converter.toPo(attempt));
        return attempt;
    }

    @Override
    public Optional<TaskExecutionAttempt> findById(String executionAttemptId) {
        return Optional.ofNullable(dao.findById(executionAttemptId)).map(converter::toDomain);
    }

    @Override
    public Optional<TaskExecutionAttempt> findCurrentByAssignmentId(String assignmentId) {
        return Optional.ofNullable(dao.findCurrentByAssignmentId(assignmentId)).map(converter::toDomain);
    }

    @Override
    public List<TaskExecutionAttempt> findByTaskId(String taskId, int limit) {
        return dao.findByTaskId(taskId, cap(limit)).stream().map(converter::toDomain).toList();
    }

    @Override
    public int countByTaskId(String taskId) {
        return dao.countByTaskId(taskId);
    }

    @Override
    public String mode() { return "MYBATIS"; }

    private int cap(int limit) { return Math.max(1, Math.min(limit, 1000)); }
}
