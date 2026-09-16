package com.opensocket.aievent.database.persistence.task.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.converter.TaskDomainPersistenceConverter;
import com.opensocket.aievent.database.persistence.task.dao.TaskRelationshipDao;
import com.opensocket.aievent.core.task.domain.TaskRelationship;
import com.opensocket.aievent.core.task.domain.TaskRelationshipRepository;
import com.opensocket.aievent.core.task.domain.TaskRelationshipType;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
public class MybatisTaskRelationshipRepository implements TaskRelationshipRepository {
    private final TaskRelationshipDao dao; private final TaskDomainPersistenceConverter converter;
    public MybatisTaskRelationshipRepository(TaskRelationshipDao dao, TaskDomainPersistenceConverter converter){this.dao=dao;this.converter=converter;}
    public TaskRelationship save(TaskRelationship value){
        if(value.getIdempotencyKey()!=null){Optional<TaskRelationship> existing=findByIdempotencyKey(value.getTenantId(),value.getIdempotencyKey());if(existing.isPresent())return existing.get();}
        dao.insert(converter.toPo(value));
        return findNatural(value.getTenantId(),value.getFromTaskId(),value.getToTaskId(),value.getRelationshipType()).orElse(value);
    }
    public Optional<TaskRelationship> findById(String tenantId,String relationshipId){return Optional.ofNullable(dao.findById(tenantId,relationshipId)).map(converter::toDomain);}
    public Optional<TaskRelationship> findByIdempotencyKey(String tenantId,String key){return key==null||key.isBlank()?Optional.empty():Optional.ofNullable(dao.findByIdempotencyKey(tenantId,key)).map(converter::toDomain);}
    public Optional<TaskRelationship> findNatural(String tenantId,String fromTaskId,String toTaskId,TaskRelationshipType type){return type==null?Optional.empty():Optional.ofNullable(dao.findNatural(tenantId,fromTaskId,toTaskId,type.name())).map(converter::toDomain);}
    public List<TaskRelationship> findOutbound(String tenantId,String taskId,int limit){return dao.findOutbound(tenantId,taskId,cap(limit)).stream().map(converter::toDomain).toList();}
    public List<TaskRelationship> findInbound(String tenantId,String taskId,int limit){return dao.findInbound(tenantId,taskId,cap(limit)).stream().map(converter::toDomain).toList();}
    public boolean delete(String tenantId,String relationshipId){return dao.delete(tenantId,relationshipId)>0;}
    public String mode(){return "MYBATIS";} private int cap(int limit){return Math.max(1,Math.min(limit,1000));}
}
