package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RolePermissionRepository;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisRolePermissionRepository implements RolePermissionRepository {
    private final IamRbacDao dao;
    public MybatisRolePermissionRepository(IamRbacDao dao){this.dao=dao;}
    public List<RolePermissionGrant> findByRoleIds(String tenantId,Set<RoleId> ids){
        if(ids==null||ids.isEmpty())return List.of();
        return dao.findRolePermissions(blankToNull(tenantId),ids.stream().map(RoleId::value).sorted().toList())
                .stream().map(this::domain).toList();
    }
    public void replace(String tenantId,RoleId roleId,List<RolePermissionGrant> grants){
        dao.deleteRolePermissions(blankToNull(tenantId),roleId.value());
        for(RolePermissionGrant grant:grants){
            if(dao.insertRolePermission(row(grant))!=1)throw new IllegalStateException("ROLE_PERMISSION_INSERT_FAILED");
        }
    }
    private Map<String,Object> row(RolePermissionGrant g){
        Map<String,Object> row=new HashMap<>();row.put("grantId",g.grantId());row.put("tenantId",g.tenantId().orElse(null));
        row.put("roleId",g.roleId().value());row.put("permissionCode",g.permissionCode().value());
        row.put("createdAt",g.createdAt());row.put("createdBy",g.createdBy());row.put("version",g.version());return row;
    }
    private RolePermissionGrant domain(Map<String,Object> r){String tenant=string(r,"tenantId");return new RolePermissionGrant(string(r,"grantId"),tenant==null?Optional.empty():Optional.of(tenant),new RoleId(string(r,"roleId")),new PermissionCode(string(r,"permissionCode")),instant(r,"createdAt"),string(r,"createdBy"),longValue(r,"version"));}
    private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
}
