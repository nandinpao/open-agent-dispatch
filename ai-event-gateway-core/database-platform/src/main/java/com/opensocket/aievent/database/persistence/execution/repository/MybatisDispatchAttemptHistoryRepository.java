package com.opensocket.aievent.database.persistence.execution.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRepository;
import com.opensocket.aievent.database.persistence.execution.converter.DispatchAttemptHistoryPersistenceConverter;
import com.opensocket.aievent.database.persistence.execution.dao.DispatchAttemptHistoryDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "dispatch", name = "attempt-history-store", havingValue = "MYBATIS")
public class MybatisDispatchAttemptHistoryRepository implements DispatchAttemptHistoryRepository {
    private final DispatchAttemptHistoryDao dao;
    private final DispatchAttemptHistoryPersistenceConverter converter;

    public MybatisDispatchAttemptHistoryRepository(
            DispatchAttemptHistoryDao dao,
            DispatchAttemptHistoryPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public DispatchAttemptHistoryRecord append(DispatchAttemptHistoryRecord record) {
        dao.insert(converter.toPo(record));
        return record;
    }

    @Override
    public List<DispatchAttemptHistoryRecord> findByTaskId(String taskId, int limit) {
        return dao.findByTaskId(taskId, cap(limit)).stream().map(converter::toRecord).toList();
    }

    @Override
    public List<DispatchAttemptHistoryRecord> findByDispatchRequestId(String dispatchRequestId, int limit) {
        return dao.findByDispatchRequestId(dispatchRequestId, cap(limit)).stream().map(converter::toRecord).toList();
    }

    @Override
    public List<DispatchAttemptHistoryRecord> recent(int limit) {
        return dao.recent(cap(limit)).stream().map(converter::toRecord).toList();
    }

    @Override
    public List<DispatchAttemptHistoryRecord> findSince(OffsetDateTime since, int limit) {
        return dao.findSince(since, cap(limit)).stream().map(converter::toRecord).toList();
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 1000));
    }
}
