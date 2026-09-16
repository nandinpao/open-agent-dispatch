package com.opensocket.aievent.core.iam.organization.application.service;

import com.opensocket.aievent.core.iam.organization.application.port.in.OrganizationQueryPort;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentMembershipRepository;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentRepository;
import com.opensocket.aievent.core.iam.organization.application.port.out.GroupMembershipRepository;
import com.opensocket.aievent.core.iam.organization.application.port.out.GroupRepository;
import com.opensocket.aievent.core.iam.organization.application.port.out.TenantMembershipRepository;
import com.opensocket.aievent.core.iam.organization.application.port.out.TenantRepository;
import com.opensocket.aievent.core.iam.organization.application.query.FindDepartmentQuery;
import com.opensocket.aievent.core.iam.organization.application.query.FindTenantQuery;
import com.opensocket.aievent.core.iam.organization.application.query.FindGroupQuery;
import com.opensocket.aievent.core.iam.organization.application.query.ResolveOrganizationScopeQuery;
import com.opensocket.aievent.core.iam.organization.domain.Department;
import com.opensocket.aievent.core.iam.organization.domain.DepartmentId;
import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembership;
import com.opensocket.aievent.core.iam.organization.domain.GroupId;
import com.opensocket.aievent.core.iam.organization.domain.Group;
import com.opensocket.aievent.core.iam.organization.domain.GroupMembership;
import com.opensocket.aievent.core.iam.organization.domain.OrganizationScope;
import com.opensocket.aievent.core.iam.organization.domain.Tenant;
import com.opensocket.aievent.core.iam.organization.domain.TenantId;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembership;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class OrganizationQueryService implements OrganizationQueryPort {
    private final TenantRepository tenants;
    private final TenantMembershipRepository tenantMemberships;
    private final DepartmentRepository departments;
    private final DepartmentMembershipRepository departmentMemberships;
    private final GroupMembershipRepository groupMemberships;
    private final GroupRepository groups;
    private final Clock clock;

    public OrganizationQueryService(
            TenantRepository tenants,
            TenantMembershipRepository tenantMemberships,
            DepartmentRepository departments,
            DepartmentMembershipRepository departmentMemberships,
            GroupMembershipRepository groupMemberships,
            GroupRepository groups,
            Clock clock
    ) {
        this.tenants = Objects.requireNonNull(tenants, "tenants");
        this.tenantMemberships = Objects.requireNonNull(tenantMemberships, "tenantMemberships");
        this.departments = Objects.requireNonNull(departments, "departments");
        this.departmentMemberships = Objects.requireNonNull(departmentMemberships, "departmentMemberships");
        this.groupMemberships = Objects.requireNonNull(groupMemberships, "groupMemberships");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<Tenant> findTenant(FindTenantQuery query) {
        return tenants.findById(new TenantId(query.tenantId()));
    }

    @Override
    public Optional<Department> findDepartment(FindDepartmentQuery query) {
        return departments.findById(new TenantId(query.tenantId()), new DepartmentId(query.departmentId()));
    }

    @Override
    public Optional<Group> findGroup(FindGroupQuery query) {
        return groups.findById(new TenantId(query.tenantId()), new GroupId(query.groupId()));
    }

    @Override
    public OrganizationScope resolveScope(ResolveOrganizationScopeQuery query) {
        TenantId tenantId = new TenantId(query.tenantId());
        PrincipalRef user = new PrincipalRef(PrincipalRef.PrincipalType.USER, query.userId());
        TenantMembership tenantMembership = tenantMemberships.find(tenantId, user)
                .orElseThrow(() -> new IllegalArgumentException("tenant membership not found"));
        List<DepartmentMembership> departmentRows = departmentMemberships.findActiveByUser(tenantId, user);
        List<GroupMembership> groupRows = groupMemberships.findActiveByUser(tenantId, user);

        Set<DepartmentId> departmentIds = departmentRows.stream()
                .map(DepartmentMembership::departmentId)
                .collect(Collectors.toUnmodifiableSet());
        Optional<DepartmentId> primaryDepartmentId = departmentRows.stream()
                .filter(DepartmentMembership::primary)
                .map(DepartmentMembership::departmentId)
                .findFirst();
        Set<GroupId> groupIds = groupRows.stream()
                .map(GroupMembership::groupId)
                .collect(Collectors.toUnmodifiableSet());
        long organizationVersion = Math.max(
                tenantMembership.version(),
                Math.max(
                        departmentRows.stream().mapToLong(DepartmentMembership::version).max().orElse(0),
                        groupRows.stream().mapToLong(GroupMembership::version).max().orElse(0)
                )
        );

        return new OrganizationScope(
                tenantId,
                user,
                tenantMembership.status(),
                primaryDepartmentId,
                departmentIds,
                groupIds,
                clock.instant(),
                organizationVersion
        );
    }
}
