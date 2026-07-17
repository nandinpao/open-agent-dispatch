package com.opensocket.aievent.database.persistence.task.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.assignment.TaskDispatchAttempt;
import com.opensocket.aievent.core.assignment.TaskDispatchAttemptRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.converter.TaskDispatchAttemptPersistenceConverter;
import com.opensocket.aievent.database.persistence.task.dao.TaskDispatchAttemptDao;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "assignment", name = "store", havingValue = "MYBATIS")
public class MybatisTaskDispatchAttemptRepository implements TaskDispatchAttemptRepository {
    private final TaskDispatchAttemptDao dao;
    private final TaskDispatchAttemptPersistenceConverter converter;

    public MybatisTaskDispatchAttemptRepository(TaskDispatchAttemptDao dao, TaskDispatchAttemptPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public TaskDispatchAttempt save(TaskDispatchAttempt attempt) {
        dao.upsert(converter.toPo(attempt));
        return attempt;
    }

    @Override
    public Optional<TaskDispatchAttempt> findById(String dispatchAttemptId) {
        return Optional.ofNullable(dao.findById(dispatchAttemptId)).map(converter::toDomain);
    }

    @Override
    public List<TaskDispatchAttempt> findByTaskId(String taskId, int limit) {
        return dao.findByTaskId(taskId, cap(limit)).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<TaskDispatchAttempt> recent(int limit) {
        return dao.recent(cap(limit)).stream().map(converter::toDomain).toList();
    }

    @Override
    public String mode() { return "MYBATIS"; }

    private int cap(int limit) { return Math.max(1, Math.min(limit, 1000)); }
}
