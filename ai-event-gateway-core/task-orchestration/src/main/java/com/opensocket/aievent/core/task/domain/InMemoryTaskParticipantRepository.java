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
public class InMemoryTaskParticipantRepository implements TaskParticipantRepository {
    private final ConcurrentHashMap<String,TaskParticipant> values=new ConcurrentHashMap<>();
    public synchronized TaskParticipant save(TaskParticipant value){Optional<TaskParticipant> byId=findById(value.getTenantId(),value.getParticipantId());if(byId.isPresent())return byId.get();Optional<TaskParticipant> existing=findNatural(value.getTenantId(),value.getTaskId(),value.getParticipantType(),value.getParticipantRefId(),value.getParticipantRole());if(existing.isPresent())return existing.get();values.put(key(value.getTenantId(),value.getParticipantId()),value);return value;}
    public Optional<TaskParticipant> findById(String tenantId,String participantId){return Optional.ofNullable(values.get(key(tenantId,participantId)));}
    public Optional<TaskParticipant> findNatural(String tenantId,String taskId,TaskParticipantType type,String refId,TaskParticipantRole role){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())&&taskId.equals(v.getTaskId())&&type==v.getParticipantType()&&refId.equals(v.getParticipantRefId())&&role==v.getParticipantRole()).findFirst();}
    public List<TaskParticipant> findByTask(String tenantId,String taskId,int limit){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())&&taskId.equals(v.getTaskId())).sorted(Comparator.comparing(TaskParticipant::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder()))).limit(Math.max(1,Math.min(limit,1000))).toList();}
    public boolean delete(String tenantId,String participantId){return values.remove(key(tenantId,participantId))!=null;}
    public String mode(){return "MEMORY";} private String key(String t,String id){return t+":"+id;}
}
