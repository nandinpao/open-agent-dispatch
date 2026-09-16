package com.opensocket.aievent.core.task.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryTaskStateHistoryRepository implements TaskStateHistoryRepository {
    private final CopyOnWriteArrayList<TaskStateHistoryEntry> values=new CopyOnWriteArrayList<>();
    public TaskStateHistoryEntry save(TaskStateHistoryEntry entry){values.add(entry);return entry;}
    public List<TaskStateHistoryEntry> findByTask(String tenantId,String taskId,int limit){return values.stream().filter(v->tenantId.equals(v.getTenantId())&&taskId.equals(v.getTaskId())).sorted(Comparator.comparing(TaskStateHistoryEntry::getTransitionAt,Comparator.nullsLast(Comparator.reverseOrder()))).limit(Math.max(1,Math.min(limit,1000))).toList();}
    public Optional<TaskStateHistoryEntry> findByIdempotencyKey(String tenantId,String idempotencyKey){if(idempotencyKey==null||idempotencyKey.isBlank())return Optional.empty();return values.stream().filter(v->tenantId.equals(v.getTenantId())&&idempotencyKey.equals(v.getIdempotencyKey())).findFirst();}
    public String mode(){return "MEMORY";}
}
