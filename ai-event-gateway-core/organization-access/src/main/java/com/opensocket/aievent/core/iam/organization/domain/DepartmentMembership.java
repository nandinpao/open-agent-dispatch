package com.opensocket.aievent.core.iam.organization.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record DepartmentMembership(MembershipId membershipId, TenantId tenantId, PrincipalRef userPrincipal,
                                   DepartmentId departmentId, DepartmentMembershipType membershipType, boolean primary,
                                   Instant effectiveAt, Optional<Instant> expiresAt, MembershipStatus status, long version) {
    public DepartmentMembership {
        Objects.requireNonNull(membershipId, "membershipId"); Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userPrincipal, "userPrincipal"); if (userPrincipal.principalType() != PrincipalRef.PrincipalType.USER) throw new OrganizationDomainException(OrganizationReasonCode.PRINCIPAL_TYPE_UNSUPPORTED, "Department membership requires USER");
        Objects.requireNonNull(departmentId, "departmentId"); Objects.requireNonNull(membershipType, "membershipType"); Objects.requireNonNull(effectiveAt, "effectiveAt");
        expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        if (expiresAt.isPresent() && !expiresAt.orElseThrow().isAfter(effectiveAt)) {
            throw new IllegalArgumentException("expiresAt must be after effectiveAt");
        }
        Objects.requireNonNull(status, "status");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }
    public DepartmentMembership update(DepartmentMembershipType type,boolean isPrimary,Optional<Instant> newExpiresAt,MembershipStatus newStatus){return new DepartmentMembership(membershipId,tenantId,userPrincipal,departmentId,type,isPrimary,effectiveAt,newExpiresAt,newStatus,version+1);}
    public DepartmentMembership remove(){return update(membershipType,false,expiresAt,MembershipStatus.REMOVED);}
}
