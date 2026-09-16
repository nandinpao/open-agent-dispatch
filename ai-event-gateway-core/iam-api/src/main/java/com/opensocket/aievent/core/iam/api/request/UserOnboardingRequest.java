package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembershipType;
import com.opensocket.aievent.core.iam.organization.domain.GroupMembershipRole;
import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Transactional Tenant user onboarding request.
 *
 * <p>The identity, Tenant Membership, organization memberships, Role Bindings and
 * optional invitation token are committed through one idempotent database transaction.</p>
 */
public record UserOnboardingRequest(
        String userId,
        @NotBlank String username,
        @Email String email,
        @NotBlank String displayName,
        @NotNull UserCreationMode creationMode,
        String authenticationMethod,
        String activationDeliveryMethod,
        @Size(max = 1024) String initialPassword,
        @NotBlank String membershipId,
        @NotNull MembershipStatus membershipStatus,
        String employeeId,
        Instant membershipExpiresAt,
        boolean defaultTenant,
        @NotNull TenantMembershipSource membershipSource,
        @Valid List<DepartmentAssignment> departments,
        @Valid List<GroupAssignment> groups,
        @Valid List<RoleAssignment> roles,
        boolean applicationAccessDeferred,
        @NotBlank @Size(max = 500) String reason) {

    public UserOnboardingRequest {
        userId = normalizeOptional(userId);
        username = username == null ? null : username.trim();
        email = normalizeOptional(email);
        displayName = displayName == null ? null : displayName.trim();
        authenticationMethod = normalizeUpper(authenticationMethod, "LOCAL");
        activationDeliveryMethod = normalizeUpper(activationDeliveryMethod, "DEVELOPMENT_FILE");
        initialPassword = normalizeOptional(initialPassword);
        if (!"LOCAL".equals(authenticationMethod)) {
            throw new IllegalArgumentException("IDENTITY_AUTHENTICATION_METHOD_NOT_AVAILABLE");
        }
        if (!List.of("EMAIL", "MANUAL", "DEVELOPMENT_FILE").contains(activationDeliveryMethod)) {
            throw new IllegalArgumentException("IDENTITY_ACTIVATION_DELIVERY_METHOD_UNSUPPORTED");
        }
        if (initialPassword != null && creationMode != UserCreationMode.ADMIN_CREATED) {
            throw new IllegalArgumentException("IDENTITY_INITIAL_PASSWORD_ADMIN_CREATED_ONLY");
        }
        if (initialPassword != null && initialPassword.length() < 14) {
            throw new IllegalArgumentException("IDENTITY_INITIAL_PASSWORD_TOO_SHORT");
        }
        departments = departments == null ? List.of() : List.copyOf(departments);
        groups = groups == null ? List.of() : List.copyOf(groups);
        roles = roles == null ? List.of() : List.copyOf(roles);
        if (applicationAccessDeferred && !roles.isEmpty()) {
            throw new IllegalArgumentException("IDENTITY_APPLICATION_ACCESS_DEFERRED_WITH_ROLE");
        }
        if (creationMode == UserCreationMode.ADMIN_CREATED
                && membershipStatus == MembershipStatus.ACTIVE
                && roles.isEmpty()
                && !applicationAccessDeferred) {
            throw new IllegalArgumentException("IDENTITY_INITIAL_RESPONSIBILITY_REQUIRED");
        }
        if (departments.stream().anyMatch(value -> value.membershipType() == DepartmentMembershipType.MANAGER)) {
            throw new IllegalArgumentException("Use Official Department Manager instead of MANAGER membership type");
        }
        if (departments.stream().filter(DepartmentAssignment::primary).count() > 1) {
            throw new IllegalArgumentException("Only one Primary Department may be selected");
        }
        if ("EMAIL".equals(activationDeliveryMethod) && initialPassword == null && (email == null || email.isBlank())) {
            throw new IllegalArgumentException("IDENTITY_ACTIVATION_EMAIL_REQUIRED");
        }
        if (creationMode == UserCreationMode.INVITATION) {
            if (membershipStatus != MembershipStatus.INVITED
                    || membershipSource != TenantMembershipSource.INVITATION
                    || defaultTenant) {
                throw new IllegalArgumentException(
                        "Invitation onboarding requires INVITED membership, INVITATION source and defaultTenant=false");
            }
        } else if (membershipStatus == MembershipStatus.INVITED
                || membershipSource == TenantMembershipSource.INVITATION) {
            throw new IllegalArgumentException(
                    "Only invitation onboarding may create an INVITED Tenant Membership");
        }
    }

    /**
     * Safe idempotency material. The temporary password is deliberately excluded so plaintext
     * credentials are never serialized into the idempotency ledger.
     */
    public Object idempotencyMaterial() {
        return java.util.Map.ofEntries(
                java.util.Map.entry("userId", userId == null ? "" : userId),
                java.util.Map.entry("username", username),
                java.util.Map.entry("email", email == null ? "" : email),
                java.util.Map.entry("displayName", displayName),
                java.util.Map.entry("creationMode", creationMode.name()),
                java.util.Map.entry("authenticationMethod", authenticationMethod),
                java.util.Map.entry("activationDeliveryMethod", activationDeliveryMethod),
                java.util.Map.entry("initialPasswordConfigured", initialPassword != null),
                java.util.Map.entry("membershipId", membershipId),
                java.util.Map.entry("membershipStatus", membershipStatus.name()),
                java.util.Map.entry("employeeId", employeeId == null ? "" : employeeId),
                java.util.Map.entry("membershipExpiresAt", membershipExpiresAt == null ? "" : membershipExpiresAt.toString()),
                java.util.Map.entry("defaultTenant", defaultTenant),
                java.util.Map.entry("membershipSource", membershipSource.name()),
                java.util.Map.entry("departments", departments),
                java.util.Map.entry("groups", groups),
                java.util.Map.entry("roles", roles),
                java.util.Map.entry("applicationAccessDeferred", applicationAccessDeferred),
                java.util.Map.entry("reason", reason));
    }

    @Override
    public String toString() {
        return "UserOnboardingRequest[userId=" + (userId == null ? "" : userId)
                + ", username=" + username
                + ", creationMode=" + creationMode
                + ", initialPasswordConfigured=" + (initialPassword != null)
                + ", membershipId=" + membershipId + "]";
    }

    public String authorizationTarget() {
        return userId == null ? "SERVER_GENERATED_USER" : userId;
    }

    private static String normalizeUpper(String value, String fallback) {
        String normalized = normalizeOptional(value);
        return normalized == null ? fallback : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record DepartmentAssignment(
            @NotBlank String membershipId,
            @NotBlank String departmentId,
            @NotNull DepartmentMembershipType membershipType,
            boolean primary,
            Instant expiresAt) { }

    public record GroupAssignment(
            @NotBlank String membershipId,
            @NotBlank String groupId,
            @NotNull GroupMembershipRole membershipRole,
            Instant expiresAt) { }

    public record RoleAssignment(
            @NotBlank String bindingId,
            @NotBlank String roleId,
            @NotBlank String scopeType,
            @NotBlank String scopeId,
            Instant effectiveAt,
            Instant expiresAt) { }
}
