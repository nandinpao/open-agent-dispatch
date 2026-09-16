package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.organization.domain.GroupMembershipRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Object-centric Group member command used by the Tenant workspace UI. */
public record AddGroupMemberRequest(
        String membershipId,
        @NotBlank String userId,
        @NotNull GroupMembershipRole membershipRole,
        Instant expiresAt) {
    public AddGroupMembershipRequest toMembershipRequest(String groupId) {
        return new AddGroupMembershipRequest(membershipId, groupId, membershipRole, expiresAt);
    }
}
