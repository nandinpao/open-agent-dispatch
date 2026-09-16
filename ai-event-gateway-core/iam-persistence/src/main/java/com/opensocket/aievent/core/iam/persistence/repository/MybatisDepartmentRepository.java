package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentRepository;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamTenantOrganizationDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DatabaseRepositoryAdapter
public class MybatisDepartmentRepository implements DepartmentRepository {
    private final IamTenantOrganizationDao dao;
    public MybatisDepartmentRepository(IamTenantOrganizationDao dao){this.dao=dao;}

    @Override public Optional<Department> findById(TenantId tenantId,DepartmentId departmentId){
        if(!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) dao.lockDepartmentHierarchy(tenantId.value());
        return Optional.ofNullable(dao.findDepartment(tenantId.value(),departmentId.value())).map(this::domain);
    }
    @Override public boolean existsByCode(TenantId tenantId,String code){return dao.countDepartmentByCode(tenantId.value(),code)>0;}
    @Override public List<Department> findActiveManagedByUser(TenantId tenantId,PrincipalRef user){return dao.findActiveDepartmentsManagedByUser(tenantId.value(),user.principalId()).stream().map(this::domain).toList();}
    @Override public List<Department> findActiveManagedByUser(PrincipalRef user){return dao.findActiveDepartmentsManagedByUserAcrossTenants(user.principalId()).stream().map(this::domain).toList();}
    @Override public long countActiveDescendants(TenantId tenantId,DepartmentId departmentId){return dao.countActiveDepartmentDescendants(tenantId.value(),departmentId.value());}
    @Override public Department save(Department value,long expectedVersion){int changed=expectedVersion==0?dao.insertDepartment(row(value)):dao.updateDepartment(row(value),expectedVersion);if(changed!=1)throw new IamOptimisticLockException("Department",value.departmentId().value(),expectedVersion);return value;}

    private Department domain(Map<String,Object> row){String manager=string(row,"managerUserId");return Department.reconstitute(new TenantId(string(row,"tenantId")),new DepartmentId(string(row,"departmentId")),string(row,"code"),string(row,"name"),Optional.ofNullable(string(row,"parentDepartmentId")).map(DepartmentId::new),manager==null?Optional.empty():Optional.of(new PrincipalRef(PrincipalRef.PrincipalType.USER,manager)),DepartmentStatus.valueOf(string(row,"status")),intValue(row,"displayOrder"),instant(row,"createdAt"),instant(row,"updatedAt"),string(row,"updatedBy"),longValue(row,"version"));}
    private Map<String,Object> row(Department value){Map<String,Object> result=new HashMap<>();result.put("tenantId",value.tenantId().value());result.put("departmentId",value.departmentId().value());result.put("code",value.code());result.put("name",value.name());result.put("parentDepartmentId",value.parentDepartmentId().map(DepartmentId::value).orElse(null));result.put("managerUserId",value.manager().map(PrincipalRef::principalId).orElse(null));result.put("status",value.status().name());result.put("displayOrder",value.displayOrder());result.put("createdAt",value.createdAt());result.put("updatedAt",value.updatedAt());result.put("updatedBy",value.updatedBy());result.put("version",value.version());return result;}
}
