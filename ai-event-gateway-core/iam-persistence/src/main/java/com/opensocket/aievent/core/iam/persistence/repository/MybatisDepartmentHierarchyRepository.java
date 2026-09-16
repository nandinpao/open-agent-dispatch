package com.opensocket.aievent.core.iam.persistence.repository;
import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*; import java.util.*; import java.util.stream.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentHierarchyRepository; import com.opensocket.aievent.core.iam.organization.domain.*; import com.opensocket.aievent.core.iam.persistence.dao.IamTenantOrganizationDao; import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
@DatabaseRepositoryAdapter public class MybatisDepartmentHierarchyRepository implements DepartmentHierarchyRepository {
 private final IamTenantOrganizationDao dao; public MybatisDepartmentHierarchyRepository(IamTenantOrganizationDao d){dao=d;}
 public DepartmentHierarchySnapshot load(TenantId t){dao.lockDepartmentHierarchy(t.value());Map<DepartmentId,DepartmentHierarchyNode> nodes=dao.loadDepartmentNodes(t.value()).stream().collect(Collectors.toUnmodifiableMap(r->new DepartmentId(string(r,"departmentId")),r->new DepartmentHierarchyNode(new DepartmentId(string(r,"departmentId")),Optional.ofNullable(string(r,"parentDepartmentId")).map(DepartmentId::new),intValue(r,"depth"))));return new DepartmentHierarchySnapshot(t,nodes);}
 public DepartmentPath loadPath(TenantId t,DepartmentId id){var rows=dao.loadDepartmentPath(t.value(),id.value());return new DepartmentPath(rows.stream().map(r->new DepartmentId(string(r,"departmentId"))).toList(),rows.stream().map(r->string(r,"code")).toList(),rows.stream().map(r->string(r,"name")).toList());}
}
