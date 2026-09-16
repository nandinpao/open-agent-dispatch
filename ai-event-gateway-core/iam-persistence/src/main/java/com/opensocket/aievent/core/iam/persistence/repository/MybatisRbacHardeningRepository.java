package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Instant;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisRbacHardeningRepository implements RbacHardeningInspectionPort,
        RbacCriticalApprovalRepository, RbacChangeEvidencePort {
    private final IamRbacDao dao;
    private final ObjectMapper mapper = new ObjectMapper();
    public MybatisRbacHardeningRepository(IamRbacDao dao){this.dao=Objects.requireNonNull(dao,"dao");}

    @Override public Set<String> activeUserIdsForGroup(String tenantId,String groupId,Instant at){
        return new LinkedHashSet<>(dao.findActiveUserIdsForGroup(tenantId,groupId,at));
    }
    @Override public Set<String> activeUserIdsForDepartment(String tenantId,String departmentId,Instant at){
        return new LinkedHashSet<>(dao.findActiveUserIdsForDepartment(tenantId,departmentId,at));
    }
    @Override public List<SeparationOfDutiesRule> activeSeparationOfDutiesRules(String tenantId){
        return dao.findActiveSeparationOfDutiesRules(tenantId).stream().map(r->new SeparationOfDutiesRule(
                string(r,"ruleId"),string(r,"tenantId"),new RoleId(string(r,"leftRoleId")),
                new RoleId(string(r,"rightRoleId")),bool(r,"scopeOverlapRequired"),string(r,"description"))).toList();
    }
    @Override public boolean scopesOverlap(String tenantId,ScopeRef left,ScopeRef right){
        return dao.r7ScopesOverlap(tenantId,left.type().name(),left.scopeId(),right.type().name(),right.scopeId());
    }
    @Override public boolean scopeContains(String tenantId,ScopeRef container,ScopeRef candidate){
        return dao.r7ScopeContains(tenantId,container.type().name(),container.scopeId(),candidate.type().name(),candidate.scopeId());
    }
    @Override public Optional<RbacCriticalChangeApproval> findById(String tenantId,String approvalId){
        return Optional.ofNullable(dao.findRbacApproval(tenantId,approvalId)).map(this::approval);
    }
    @Override public List<RbacCriticalChangeApproval> findRecent(String tenantId,String status,int limit){
        return dao.findRbacApprovals(tenantId,status==null?"":status.trim(),Math.max(1,Math.min(limit,200))).stream().map(this::approval).toList();
    }
    @Override public RbacCriticalChangeApproval save(RbacCriticalChangeApproval a,long expected){
        int rows=expected==0?dao.insertRbacApproval(row(a)):dao.updateRbacApproval(row(a),expected);
        if(rows!=1)throw new IamOptimisticLockException("RbacCriticalChangeApproval",a.approvalId(),expected);
        return a;
    }
    @Override public void append(RbacChangeEvidence e){
        if(dao.insertRbacChangeEvidence(row(e))!=1)throw new IllegalStateException("RBAC evidence insert failed");
    }
    private RbacCriticalChangeApproval approval(Map<String,Object> r){return RbacCriticalChangeApproval.reconstitute(
            string(r,"approvalId"),authorityScope(r.get("tenantId")),RbacApprovalOperation.valueOf(string(r,"operation")),
            string(r,"requestHash"),string(r,"requesterId"),string(r,"targetType"),string(r,"targetId"),
            RbacApprovalStatus.valueOf(string(r,"status")),string(r,"approverId"),string(r,"decisionReason"),
            instant(r,"requestedAt"),instant(r,"expiresAt"),instant(r,"decidedAt"),instant(r,"consumedAt"),longValue(r,"version"));}
    private Map<String,Object> row(RbacCriticalChangeApproval a){Map<String,Object> m=new HashMap<>();
        m.put("approvalId",a.approvalId());m.put("tenantId",a.tenantId());m.put("operation",a.operation().name());
        m.put("requestHash",a.requestHash());m.put("requesterId",a.requesterId());m.put("targetType",a.targetType());
        m.put("targetId",a.targetId());m.put("status",a.status().name());m.put("approverId",a.approverId());
        m.put("decisionReason",a.decisionReason());m.put("requestedAt",a.requestedAt());m.put("expiresAt",a.expiresAt());
        m.put("decidedAt",a.decidedAt());m.put("consumedAt",a.consumedAt());m.put("version",a.version());return m;}
    private Map<String,Object> row(RbacChangeEvidence e){Map<String,Object> m=new HashMap<>();
        m.put("evidenceId",e.evidenceId());m.put("tenantId",e.tenantId());m.put("operation",e.operation());
        m.put("actorId",e.actorId());m.put("targetType",e.targetType());m.put("targetId",e.targetId());
        m.put("approvalId",e.approvalId());m.put("beforeJson",e.beforeJson());m.put("afterJson",e.afterJson());
        m.put("addedPermissions",json(e.addedPermissions()));m.put("removedPermissions",json(e.removedPermissions()));
        m.put("warnings",json(e.warnings()));m.put("correlationId",e.correlationId());m.put("auditReason",e.auditReason());
        m.put("occurredAt",e.occurredAt());return m;}
    private static String authorityScope(Object value){return value==null?"INSTANCE":value.toString();}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JacksonException ex){throw new IllegalArgumentException("Cannot serialize RBAC evidence",ex);}}
}
