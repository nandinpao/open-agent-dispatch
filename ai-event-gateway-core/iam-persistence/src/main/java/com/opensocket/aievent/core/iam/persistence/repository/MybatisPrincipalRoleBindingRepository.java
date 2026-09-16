package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PrincipalRoleBindingRepository;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Instant;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisPrincipalRoleBindingRepository implements PrincipalRoleBindingRepository {
    private final IamRbacDao dao; public MybatisPrincipalRoleBindingRepository(IamRbacDao dao){this.dao=dao;}
    public Optional<PrincipalRoleBinding> findById(String tenantId,String bindingId){return Optional.ofNullable(dao.findBindingById(blankToNull(tenantId),bindingId)).map(this::domain);}
    public List<PrincipalRoleBinding> findEffective(String tenantId,Set<PrincipalRef> principals,Instant at){if(principals==null||principals.isEmpty())return List.of();List<String> keys=principals.stream().map(p->p.principalType().name()+""+p.principalId()).sorted().toList();return dao.findEffectiveBindings(blankToNull(tenantId),keys,at).stream().map(this::domain).toList();}
    public PrincipalRoleBinding save(PrincipalRoleBinding b,long expected){int rows=expected==0?dao.insertBinding(row(b)):dao.updateBinding(row(b),expected);if(rows!=1)throw new IamOptimisticLockException("PrincipalRoleBinding",b.bindingId(),expected);return b;}
    private PrincipalRoleBinding domain(Map<String,Object> r){String tenant=string(r,"tenantId");ScopeType type=ScopeType.valueOf(string(r,"scopeType"));ScopeRef scope=new ScopeRef(type,string(r,"scopeId"),tenant);return PrincipalRoleBinding.reconstitute(string(r,"bindingId"),new PrincipalRef(PrincipalRef.PrincipalType.valueOf(string(r,"principalType")),string(r,"principalId")),new RoleId(string(r,"roleId")),scope,instant(r,"effectiveAt"),instant(r,"expiresAt"),BindingStatus.valueOf(string(r,"status")),instant(r,"createdAt"),string(r,"createdBy"),instant(r,"revokedAt"),string(r,"revokedBy"),longValue(r,"version"));}
    private Map<String,Object> row(PrincipalRoleBinding b){Map<String,Object> m=new HashMap<>();m.put("bindingId",b.bindingId());m.put("tenantId",b.scope().type()==ScopeType.INSTANCE?null:b.scope().tenantId());m.put("principalType",b.principal().principalType().name());m.put("principalId",b.principal().principalId());m.put("roleId",b.roleId().value());m.put("scopeType",b.scope().type().name());m.put("scopeId",b.scope().scopeId());m.put("effectiveAt",b.effectiveAt());m.put("expiresAt",b.expiresAt());m.put("status",b.status().name());m.put("createdAt",b.createdAt());m.put("createdBy",b.createdBy());m.put("revokedAt",b.revokedAt());m.put("revokedBy",b.revokedBy());m.put("version",b.version());return m;}
    private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
}
