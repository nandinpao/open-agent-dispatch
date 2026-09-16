package com.opensocket.aievent.core.integration.issue;
import java.util.*; import java.util.concurrent.ConcurrentHashMap; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Repository; import com.opensocket.aievent.core.issue.*;
@Repository @Profile("!prod") @ConditionalOnProperty(prefix="task-issue-links",name="store",havingValue="MEMORY",matchIfMissing=true)
public class InMemoryTaskIssueLinkRepository implements TaskIssueLinkRepository {
 private final Map<String,TaskIssueLink> values=new ConcurrentHashMap<>(); private String k(String t,String id){return String.valueOf(t)+":"+id;}
 public TaskIssueLink save(TaskIssueLink v){if(v.getLinkId()==null)v.setLinkId("task-issue-link-"+UUID.randomUUID());values.put(k(v.getTenantId(),v.getLinkId()),v);return v;}
 public Optional<TaskIssueLink> findByTenantAndLinkId(String t,String id){return Optional.ofNullable(values.get(k(t,id)));}
 public Optional<TaskIssueLink> findByTenantAndIdempotencyKey(String t,String key){return values.values().stream().filter(v->Objects.equals(t,v.getTenantId())&&Objects.equals(key,v.getIdempotencyKey())).findFirst();}
 public Optional<TaskIssueLink> findByExternalIssue(String t,String c,String p,String i){return values.values().stream().filter(v->Objects.equals(t,v.getTenantId())&&Objects.equals(c,v.getConnectionId())&&Objects.equals(p,v.getExternalProjectId())&&Objects.equals(i,v.getExternalIssueId())).findFirst();}
 public List<TaskIssueLink> findAllByTenantAndTaskId(String t,String task){return values.values().stream().filter(v->(t==null||Objects.equals(t,v.getTenantId()))&&Objects.equals(task,v.getTaskId())).sorted(Comparator.comparing(TaskIssueLink::getUpdatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).toList();}
 public List<TaskIssueLink> findAllByTenantAndTaskIds(String t,List<String> ids){return values.values().stream().filter(v->(t==null||Objects.equals(t,v.getTenantId()))&&ids.contains(v.getTaskId())).toList();}
 public List<TaskIssueLink> recent(String t,int n){return values.values().stream().filter(v->t==null||Objects.equals(t,v.getTenantId())).sorted(Comparator.comparing(TaskIssueLink::getUpdatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).limit(n).toList();} public String mode(){return "MEMORY";}
}
