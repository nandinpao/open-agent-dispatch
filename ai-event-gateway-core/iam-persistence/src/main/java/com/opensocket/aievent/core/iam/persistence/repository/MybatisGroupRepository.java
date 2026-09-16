package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.GroupRepository;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamTenantOrganizationDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisGroupRepository implements GroupRepository {
    private final IamTenantOrganizationDao dao;
    public MybatisGroupRepository(IamTenantOrganizationDao dao){this.dao=dao;}
    @Override public Optional<Group> findById(TenantId tenantId,GroupId groupId){return Optional.ofNullable(dao.findGroup(tenantId.value(),groupId.value())).map(this::domain);}
    @Override public boolean existsByCode(TenantId tenantId,String code){return dao.countGroupByCode(tenantId.value(),code)>0;}
    @Override public long countActiveChildren(TenantId tenantId,GroupId groupId){return dao.countActiveGroupChildren(tenantId.value(),groupId.value());}
    @Override public long countActiveByOwnerDepartment(TenantId tenantId,DepartmentId departmentId){return dao.countActiveGroupsByOwnerDepartment(tenantId.value(),departmentId.value());}
    @Override public Group save(Group value,long expectedVersion){int changed=expectedVersion==0?dao.insertGroup(row(value)):dao.updateGroup(row(value),expectedVersion);if(changed!=1)throw new IamOptimisticLockException("Group",value.groupId().value(),expectedVersion);return value;}
    private Group domain(Map<String,Object> row){return Group.reconstitute(new TenantId(string(row,"tenantId")),new GroupId(string(row,"groupId")),string(row,"code"),string(row,"name"),GroupType.valueOf(string(row,"type")),Optional.ofNullable(string(row,"parentGroupId")).map(GroupId::new),Optional.ofNullable(string(row,"ownerDepartmentId")).map(DepartmentId::new),GroupStatus.valueOf(string(row,"status")),string(row,"description"),instant(row,"createdAt"),instant(row,"updatedAt"),string(row,"updatedBy"),longValue(row,"version"));}
    private Map<String,Object> row(Group value){Map<String,Object> result=new HashMap<>();result.put("tenantId",value.tenantId().value());result.put("groupId",value.groupId().value());result.put("code",value.code());result.put("name",value.name());result.put("type",value.type().name());result.put("parentGroupId",value.parentGroupId().map(GroupId::value).orElse(null));result.put("ownerDepartmentId",value.ownerDepartmentId().map(DepartmentId::value).orElse(null));result.put("status",value.status().name());result.put("description",value.description());result.put("createdAt",value.createdAt());result.put("updatedAt",value.updatedAt());result.put("updatedBy",value.updatedBy());result.put("version",value.version());return result;}
}
