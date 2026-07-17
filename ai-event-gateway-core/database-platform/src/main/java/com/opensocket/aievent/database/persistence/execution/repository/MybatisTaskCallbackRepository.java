package com.opensocket.aievent.database.persistence.execution.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackRepository;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.database.persistence.execution.dao.TaskCallbackDao;
import com.opensocket.aievent.database.persistence.execution.po.TaskCallbackPo;
import com.opensocket.aievent.database.persistence.execution.converter.TaskCallbackPersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "task.callback", name = "store", havingValue = "MYBATIS")
public class MybatisTaskCallbackRepository implements TaskCallbackRepository {
    private final TaskCallbackDao dao;
    private final TaskCallbackPersistenceConverter converter;

    public MybatisTaskCallbackRepository(TaskCallbackDao dao, TaskCallbackPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
        public TaskCallbackRecord save(TaskCallbackRecord record) {
            dao.upsert(converter.toPo(record));
            return record;
        }

    @Override
        public boolean tryReserve(TaskCallbackRecord record) {
            try {
                dao.insert(converter.toPo(record));
                return true;
            } catch (DuplicateKeyException ex) {
                return false;
            }
        }

    @Override
        public Optional<TaskCallbackRecord> findByCallbackId(String callbackId) {
            return Optional.ofNullable(dao.findByCallbackId(callbackId)).map(converter::toRecord);
        }

    @Override
        public List<TaskCallbackRecord> findByTaskId(String taskId, int limit) {
            return dao.findByTaskId(taskId, cap(limit)).stream().map(converter::toRecord).toList();
        }

    @Override
        public List<TaskCallbackRecord> findByDispatchRequestId(String dispatchRequestId, int limit) {
            return dao.findByDispatchRequestId(dispatchRequestId, cap(limit)).stream().map(converter::toRecord).toList();
        }

    @Override
        public List<TaskCallbackRecord> recent(int limit) {
            return dao.recent(cap(limit)).stream().map(converter::toRecord).toList();
        }

    @Override
        public String mode() { return "MYBATIS"; }

    private int cap(int limit) { return Math.max(1, Math.min(limit, 1000)); }
}
