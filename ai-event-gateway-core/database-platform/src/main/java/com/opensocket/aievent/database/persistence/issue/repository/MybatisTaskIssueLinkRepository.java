package com.opensocket.aievent.database.persistence.issue.repository;
import java.util.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.core.issue.*; import com.opensocket.aievent.database.persistence.issue.converter.TaskIssueLinkPersistenceConverter;
import com.opensocket.aievent.database.persistence.issue.dao.TaskIssueLinkDao; import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
@DatabaseRepositoryAdapter @ConditionalOnProperty(prefix="task-issue-links",name="store",havingValue="MYBATIS")
public class MybatisTaskIssueLinkRepository implements TaskIssueLinkRepository {
 private final TaskIssueLinkDao dao; private final TaskIssueLinkPersistenceConverter converter;
 public MybatisTaskIssueLinkRepository(TaskIssueLinkDao dao,TaskIssueLinkPersistenceConverter converter){this.dao=dao;this.converter=converter;}
 public TaskIssueLink save(TaskIssueLink link){if(link==null||blank(link.getTaskId()))throw new IllegalArgumentException("task issue link with taskId is required");if(blank(link.getLinkId()))link.setLinkId("task-issue-link-"+UUID.randomUUID());dao.upsert(converter.toPo(link));return link;}
 public Optional<TaskIssueLink> findByTenantAndLinkId(String tenant,String id){if(blank(id))return Optional.empty();return Optional.ofNullable(dao.findByTenantAndLinkId(tenant,id)).map(converter::toDomain);}
 public Optional<TaskIssueLink> findByTenantAndIdempotencyKey(String tenant,String key){if(blank(key))return Optional.empty();return Optional.ofNullable(dao.findByTenantAndIdempotencyKey(tenant,key)).map(converter::toDomain);}
 public Optional<TaskIssueLink> findByExternalIssue(String tenant,String connection,String project,String issue){return Optional.ofNullable(dao.findByExternalIssue(tenant,connection,project,issue)).map(converter::toDomain);}
 public List<TaskIssueLink> findAllByTenantAndTaskId(String tenant,String task){if(blank(task))return List.of();return dao.findAllByTenantAndTaskId(tenant,task).stream().map(converter::toDomain).toList();}
 public List<TaskIssueLink> findAllByTenantAndTaskIds(String tenant,List<String> ids){if(ids==null||ids.isEmpty())return List.of();var safe=ids.stream().filter(v->!blank(v)).distinct().limit(1000).toList();return safe.isEmpty()?List.of():dao.findAllByTenantAndTaskIds(tenant,safe).stream().map(converter::toDomain).toList();}
 public List<TaskIssueLink> recent(String tenant,int limit){return dao.recent(tenant,Math.max(1,Math.min(limit,1000))).stream().map(converter::toDomain).toList();}
 public String mode(){return "MYBATIS";} private boolean blank(String v){return v==null||v.isBlank();}
}
