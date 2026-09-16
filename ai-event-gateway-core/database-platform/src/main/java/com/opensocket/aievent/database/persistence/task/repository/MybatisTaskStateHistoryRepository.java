package com.opensocket.aievent.database.persistence.task.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.converter.TaskDomainPersistenceConverter;
import com.opensocket.aievent.database.persistence.task.dao.TaskStateHistoryDao;
import com.opensocket.aievent.core.task.domain.TaskStateHistoryEntry;
import com.opensocket.aievent.core.task.domain.TaskStateHistoryRepository;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
public class MybatisTaskStateHistoryRepository implements TaskStateHistoryRepository {
    private final TaskStateHistoryDao dao; private final TaskDomainPersistenceConverter converter;
    public MybatisTaskStateHistoryRepository(TaskStateHistoryDao dao,TaskDomainPersistenceConverter converter){this.dao=dao;this.converter=converter;}
    public TaskStateHistoryEntry save(TaskStateHistoryEntry entry){dao.insert(converter.toPo(entry));return entry;}
    public List<TaskStateHistoryEntry> findByTask(String tenantId,String taskId,int limit){return dao.findByTask(tenantId,taskId,Math.max(1,Math.min(limit,1000))).stream().map(converter::toDomain).toList();}
    public Optional<TaskStateHistoryEntry> findByIdempotencyKey(String tenantId,String idempotencyKey){if(idempotencyKey==null||idempotencyKey.isBlank())return Optional.empty();return dao.findByIdempotencyKey(tenantId,idempotencyKey.trim()).map(converter::toDomain);}
    public String mode(){return "MYBATIS";}
}
