package com.opensocket.aievent.core.iam.api.request;
import com.opensocket.aievent.core.iam.organization.domain.GroupMembershipRole;import jakarta.validation.constraints.*;import java.time.Instant;
public record UpdateGroupMembershipRequest(@NotNull GroupMembershipRole membershipRole,Instant expiresAt,@NotBlank @Size(max=500) String reason) { }
