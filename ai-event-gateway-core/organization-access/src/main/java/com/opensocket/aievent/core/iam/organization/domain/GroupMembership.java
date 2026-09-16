package com.opensocket.aievent.core.iam.organization.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record GroupMembership(MembershipId membershipId, TenantId tenantId, PrincipalRef userPrincipal, GroupId groupId,
                              GroupMembershipRole membershipRole, Instant effectiveAt, Optional<Instant> expiresAt,
                              MembershipStatus status, long version) {
    public GroupMembership {
        Objects.requireNonNull(membershipId, "membershipId"); Objects.requireNonNull(tenantId, "tenantId"); Objects.requireNonNull(userPrincipal, "userPrincipal");
        if (userPrincipal.principalType() != PrincipalRef.PrincipalType.USER) throw new OrganizationDomainException(OrganizationReasonCode.PRINCIPAL_TYPE_UNSUPPORTED, "Group membership requires USER");
        Objects.requireNonNull(groupId, "groupId"); Objects.requireNonNull(membershipRole, "membershipRole");
        Objects.requireNonNull(effectiveAt, "effectiveAt"); expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        if (expiresAt.isPresent() && !expiresAt.orElseThrow().isAfter(effectiveAt)) {
            throw new IllegalArgumentException("expiresAt must be after effectiveAt");
        }
        Objects.requireNonNull(status, "status");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }
    public GroupMembership update(GroupMembershipRole role,Optional<Instant> newExpiresAt,MembershipStatus newStatus){return new GroupMembership(membershipId,tenantId,userPrincipal,groupId,role,effectiveAt,newExpiresAt,newStatus,version+1);}
    public GroupMembership remove(){return update(membershipRole,expiresAt,MembershipStatus.REMOVED);}
}
