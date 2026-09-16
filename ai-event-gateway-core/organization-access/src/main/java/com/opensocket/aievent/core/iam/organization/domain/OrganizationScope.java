package com.opensocket.aievent.core.iam.organization.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable current organization scope. It is an input to RBAC, not an authorization decision. */
public record OrganizationScope(TenantId tenantId, PrincipalRef principal, MembershipStatus tenantMembershipStatus,
                                Optional<DepartmentId> primaryDepartmentId, Set<DepartmentId> departmentIds,
                                Set<GroupId> groupIds, Instant resolvedAt, long organizationVersion) {
    public OrganizationScope {
        Objects.requireNonNull(tenantId, "tenantId"); Objects.requireNonNull(principal, "principal"); Objects.requireNonNull(tenantMembershipStatus, "tenantMembershipStatus");
        primaryDepartmentId = primaryDepartmentId == null ? Optional.empty() : primaryDepartmentId;
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
        groupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds);
        if (primaryDepartmentId.isPresent() && !departmentIds.contains(primaryDepartmentId.orElseThrow())) {
            throw new IllegalArgumentException("primary department must be included in departmentIds");
        }
        Objects.requireNonNull(resolvedAt, "resolvedAt"); if (organizationVersion < 0) throw new IllegalArgumentException("organizationVersion must be non-negative");
    }

    public boolean activeTenantMember() { return tenantMembershipStatus == MembershipStatus.ACTIVE; }
}
