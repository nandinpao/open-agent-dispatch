package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentMembershipRepository;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamTenantOrganizationDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisDepartmentMembershipRepository implements DepartmentMembershipRepository {
    private final IamTenantOrganizationDao dao;
    public MybatisDepartmentMembershipRepository(IamTenantOrganizationDao dao){this.dao=dao;}
    @Override public Optional<DepartmentMembership> findById(TenantId tenantId,MembershipId id){return Optional.ofNullable(dao.findDepartmentMembership(tenantId.value(),id.value())).map(this::domain);}
    @Override public Optional<DepartmentMembership> findByUserAndDepartment(TenantId tenantId,PrincipalRef user,DepartmentId departmentId){return Optional.ofNullable(dao.findDepartmentMembershipByUserAndDepartment(tenantId.value(),user.principalId(),departmentId.value())).map(this::domain);}
    @Override public Optional<DepartmentMembership> findStoredPrimaryByUser(TenantId tenantId,PrincipalRef user){return Optional.ofNullable(dao.findStoredPrimaryDepartmentMembership(tenantId.value(),user.principalId())).map(this::domain);}
    @Override public List<DepartmentMembership> findActiveByUser(TenantId tenantId,PrincipalRef user){return dao.findDepartmentMemberships(tenantId.value(),user.principalId()).stream().map(this::domain).toList();}
    @Override public long countActiveByDepartment(TenantId tenantId,DepartmentId departmentId){return dao.countActiveDepartmentMemberships(tenantId.value(),departmentId.value());}
    @Override public DepartmentMembership save(DepartmentMembership value,long expectedVersion){int changed=expectedVersion==0?dao.insertDepartmentMembership(row(value)):dao.updateDepartmentMembership(row(value),expectedVersion);if(changed!=1)throw new IamOptimisticLockException("DepartmentMembership",value.membershipId().value(),expectedVersion);return value;}
    private DepartmentMembership domain(Map<String,Object> row){return new DepartmentMembership(new MembershipId(string(row,"membershipId")),new TenantId(string(row,"tenantId")),new PrincipalRef(PrincipalRef.PrincipalType.USER,string(row,"userId")),new DepartmentId(string(row,"departmentId")),DepartmentMembershipType.valueOf(string(row,"membershipType")),bool(row,"primary"),instant(row,"effectiveAt"),Optional.ofNullable(instant(row,"expiresAt")),MembershipStatus.valueOf(string(row,"status")),longValue(row,"version"));}
    private Map<String,Object> row(DepartmentMembership value){Map<String,Object> result=new HashMap<>();result.put("tenantId",value.tenantId().value());result.put("membershipId",value.membershipId().value());result.put("userId",value.userPrincipal().principalId());result.put("departmentId",value.departmentId().value());result.put("membershipType",value.membershipType().name());result.put("primary",value.primary());result.put("effectiveAt",value.effectiveAt());result.put("expiresAt",value.expiresAt().orElse(null));result.put("status",value.status().name());result.put("version",value.version());return result;}
}
