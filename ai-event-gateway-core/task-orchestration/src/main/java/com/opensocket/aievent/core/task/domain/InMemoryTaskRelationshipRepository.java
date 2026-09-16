package com.opensocket.aievent.core.task.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryTaskRelationshipRepository implements TaskRelationshipRepository {
    private final ConcurrentHashMap<String,TaskRelationship> values=new ConcurrentHashMap<>();
    public synchronized TaskRelationship save(TaskRelationship value){
        if(value.getIdempotencyKey()!=null){Optional<TaskRelationship> replay=findByIdempotencyKey(value.getTenantId(),value.getIdempotencyKey());if(replay.isPresent())return replay.get();}
        Optional<TaskRelationship> natural=findNatural(value.getTenantId(),value.getFromTaskId(),value.getToTaskId(),value.getRelationshipType());
        if(natural.isPresent())return natural.get();
        values.put(key(value.getTenantId(),value.getRelationshipId()),value);return value;
    }
    public Optional<TaskRelationship> findById(String tenantId,String relationshipId){return Optional.ofNullable(values.get(key(tenantId,relationshipId)));}
    public Optional<TaskRelationship> findByIdempotencyKey(String tenantId,String idempotencyKey){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())&&idempotencyKey!=null&&idempotencyKey.equals(v.getIdempotencyKey())).findFirst();}
    public Optional<TaskRelationship> findNatural(String tenantId,String fromTaskId,String toTaskId,TaskRelationshipType type){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())&&fromTaskId.equals(v.getFromTaskId())&&toTaskId.equals(v.getToTaskId())&&type==v.getRelationshipType()).findFirst();}
    public List<TaskRelationship> findOutbound(String tenantId,String taskId,int limit){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())&&taskId.equals(v.getFromTaskId())).sorted(Comparator.comparing(TaskRelationship::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).limit(cap(limit)).toList();}
    public List<TaskRelationship> findInbound(String tenantId,String taskId,int limit){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())&&taskId.equals(v.getToTaskId())).sorted(Comparator.comparing(TaskRelationship::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).limit(cap(limit)).toList();}
    public boolean delete(String tenantId,String relationshipId){return values.remove(key(tenantId,relationshipId))!=null;}
    public String mode(){return "MEMORY";} private String key(String t,String id){return t+":"+id;} private int cap(int n){return Math.max(1,Math.min(n,1000));}
}
