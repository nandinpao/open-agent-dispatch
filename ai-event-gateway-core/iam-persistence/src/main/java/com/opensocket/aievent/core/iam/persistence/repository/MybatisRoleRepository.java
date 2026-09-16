package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RoleRepository;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisRoleRepository implements RoleRepository {
    private final IamRbacDao dao; public MybatisRoleRepository(IamRbacDao dao){this.dao=dao;}
    public Optional<Role> findById(String tenantId,RoleId id){return Optional.ofNullable(dao.findRoleById(blankToNull(tenantId),id.value())).map(this::domain);}
    public Optional<Role> findByCode(String tenantId,RoleCode code){return Optional.ofNullable(dao.findRoleByCode(blankToNull(tenantId),code.value())).map(this::domain);}
    public List<Role> findByIds(String tenantId,Set<RoleId> ids){if(ids==null||ids.isEmpty())return List.of();return dao.findRolesByIds(blankToNull(tenantId),ids.stream().map(RoleId::value).sorted().toList()).stream().map(this::domain).toList();}
    public Role save(Role role,long expected){int rows=expected==0?dao.insertRole(row(role)):dao.updateRole(row(role),expected);if(rows!=1)throw new IamOptimisticLockException("Role",role.roleId().value(),expected);return role;}
    private Role domain(Map<String,Object> r){String tenant=string(r,"tenantId");return Role.reconstitute(new RoleId(string(r,"roleId")),tenant==null?Optional.empty():Optional.of(tenant),new RoleCode(string(r,"roleCode")),string(r,"roleName"),string(r,"description"),RoleType.valueOf(string(r,"roleType")),RoleStatus.valueOf(string(r,"status")),bool(r,"systemManaged"),instant(r,"createdAt"),instant(r,"updatedAt"),string(r,"createdBy"),string(r,"updatedBy"),longValue(r,"version"));}
    private Map<String,Object> row(Role r){Map<String,Object> m=new HashMap<>();m.put("roleId",r.roleId().value());m.put("tenantId",r.tenantId().orElse(null));m.put("roleCode",r.roleCode().value());m.put("roleName",r.roleName());m.put("description",r.description());m.put("roleType",r.roleType().name());m.put("status",r.status().name());m.put("systemManaged",r.systemManaged());m.put("createdAt",r.createdAt());m.put("updatedAt",r.updatedAt());m.put("createdBy",r.createdBy());m.put("updatedBy",r.updatedBy());m.put("version",r.version());return m;}
    private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
}
