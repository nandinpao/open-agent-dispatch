package com.opensocket.aievent.core.iam.organization.application.port.in;
import com.opensocket.aievent.core.iam.organization.application.query.*;import com.opensocket.aievent.core.iam.organization.domain.*;import java.util.Optional;
public interface OrganizationQueryPort { Optional<Tenant> findTenant(FindTenantQuery query); Optional<Department> findDepartment(FindDepartmentQuery query); Optional<Group> findGroup(FindGroupQuery query); OrganizationScope resolveScope(ResolveOrganizationScopeQuery query); }
