package com.opensocket.aievent.database.persistence.task.repository;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.dao.TaskLineageEvidenceDao;
import com.opensocket.aievent.database.persistence.task.po.TaskLineageEvidencePo;
import com.opensocket.aievent.core.task.lineage.TaskFailureDomain;
import com.opensocket.aievent.core.task.lineage.TaskLineageEventType;
import com.opensocket.aievent.core.task.lineage.TaskLineageEvidence;
import com.opensocket.aievent.core.task.lineage.TaskLineageRepository;
import com.opensocket.aievent.core.task.lineage.TaskLineageQuery;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="task",name="store",havingValue="MYBATIS")
public class MybatisTaskLineageRepository implements TaskLineageRepository {
    private final TaskLineageEvidenceDao dao;
    public MybatisTaskLineageRepository(TaskLineageEvidenceDao dao){this.dao=dao;}
    @Override public TaskLineageEvidence append(TaskLineageEvidence e){dao.insert(toPo(e));return e;}
    @Override public List<TaskLineageEvidence> findByTask(String t,String id,int n){return dao.findByTask(t,id,cap(n)).stream().map(this::fromPo).toList();}
    @Override public List<TaskLineageEvidence> findByRootTask(String t,String id,int n){return dao.findByRootTask(t,id,cap(n)).stream().map(this::fromPo).toList();}
    @Override public List<TaskLineageEvidence> findByAgent(String t,String id,int n){return dao.findByAgent(t,id,cap(n)).stream().map(this::fromPo).toList();}
    @Override public List<TaskLineageEvidence> findByCorrelation(String t,String id,int n){return dao.findByCorrelation(t,id,cap(n)).stream().map(this::fromPo).toList();}
    @Override public List<TaskLineageEvidence> search(TaskLineageQuery q){return q==null?List.of():dao.search(q).stream().map(this::fromPo).toList();}
    private int cap(int n){return Math.max(1,Math.min(n,1000));}
    private TaskLineageEvidencePo toPo(TaskLineageEvidence e){TaskLineageEvidencePo p=new TaskLineageEvidencePo();p.setTenantId(e.getTenantId());p.setEvidenceId(e.getEvidenceId());p.setRootTaskId(e.getRootTaskId());p.setTaskId(e.getTaskId());p.setParentTaskId(e.getParentTaskId());p.setEventType(e.getEventType().name());p.setOriginPrincipalType(e.getOriginPrincipalType());p.setOriginPrincipalId(e.getOriginPrincipalId());p.setActorPrincipalType(e.getActorPrincipalType());p.setActorPrincipalId(e.getActorPrincipalId());p.setExecutorAgentId(e.getExecutorAgentId());p.setAssignmentId(e.getAssignmentId());p.setDepartmentId(e.getDepartmentId());p.setGroupId(e.getGroupId());p.setCredentialId(e.getCredentialId());p.setOauthClientId(e.getOauthClientId());p.setSourceSystem(e.getSourceSystem());p.setCorrelationId(e.getCorrelationId());p.setTraceId(e.getTraceId());p.setFailureDomain(e.getFailureDomain().name());p.setFailureCode(e.getFailureCode());p.setReason(e.getReason());p.setOccurredAt(e.getOccurredAt());return p;}
    private TaskLineageEvidence fromPo(TaskLineageEvidencePo p){TaskLineageEvidence e=new TaskLineageEvidence();e.setTenantId(p.getTenantId());e.setEvidenceId(p.getEvidenceId());e.setRootTaskId(p.getRootTaskId());e.setTaskId(p.getTaskId());e.setParentTaskId(p.getParentTaskId());e.setEventType(TaskLineageEventType.valueOf(p.getEventType()));e.setOriginPrincipalType(p.getOriginPrincipalType());e.setOriginPrincipalId(p.getOriginPrincipalId());e.setActorPrincipalType(p.getActorPrincipalType());e.setActorPrincipalId(p.getActorPrincipalId());e.setExecutorAgentId(p.getExecutorAgentId());e.setAssignmentId(p.getAssignmentId());e.setDepartmentId(p.getDepartmentId());e.setGroupId(p.getGroupId());e.setCredentialId(p.getCredentialId());e.setOauthClientId(p.getOauthClientId());e.setSourceSystem(p.getSourceSystem());e.setCorrelationId(p.getCorrelationId());e.setTraceId(p.getTraceId());e.setFailureDomain(p.getFailureDomain()==null?TaskFailureDomain.NONE:TaskFailureDomain.valueOf(p.getFailureDomain()));e.setFailureCode(p.getFailureCode());e.setReason(p.getReason());e.setOccurredAt(p.getOccurredAt());return e;}
}
