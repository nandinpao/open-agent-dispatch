package com.opensocket.aievent.database.persistence.task.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.converter.TaskDomainPersistenceConverter;
import com.opensocket.aievent.database.persistence.task.dao.TaskParticipantDao;
import com.opensocket.aievent.core.task.domain.TaskParticipant;
import com.opensocket.aievent.core.task.domain.TaskParticipantRepository;
import com.opensocket.aievent.core.task.domain.TaskParticipantType;
import com.opensocket.aievent.core.task.domain.TaskParticipantRole;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
public class MybatisTaskParticipantRepository implements TaskParticipantRepository {
    private final TaskParticipantDao dao; private final TaskDomainPersistenceConverter converter;
    public MybatisTaskParticipantRepository(TaskParticipantDao dao,TaskDomainPersistenceConverter converter){this.dao=dao;this.converter=converter;}
    public TaskParticipant save(TaskParticipant value){dao.insert(converter.toPo(value));return findNatural(value.getTenantId(),value.getTaskId(),value.getParticipantType(),value.getParticipantRefId(),value.getParticipantRole()).orElse(value);}
    public Optional<TaskParticipant> findById(String tenantId,String participantId){return Optional.ofNullable(dao.findById(tenantId,participantId)).map(converter::toDomain);}
    public Optional<TaskParticipant> findNatural(String tenantId,String taskId,TaskParticipantType type,String refId,TaskParticipantRole role){return type==null||role==null?Optional.empty():Optional.ofNullable(dao.findNatural(tenantId,taskId,type.name(),refId,role.name())).map(converter::toDomain);}
    public List<TaskParticipant> findByTask(String tenantId,String taskId,int limit){return dao.findByTask(tenantId,taskId,Math.max(1,Math.min(limit,1000))).stream().map(converter::toDomain).toList();}
    public boolean delete(String tenantId,String participantId){return dao.delete(tenantId,participantId)>0;}
    public String mode(){return "MYBATIS";}
}
