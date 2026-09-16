package com.opensocket.aievent.core.iam.organization.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Tenant-owned membership aggregate. Global user identity remains in identity-core. */
public record TenantMembership(
        MembershipId membershipId,
        TenantId tenantId,
        PrincipalRef userPrincipal,
        MembershipStatus status,
        Optional<String> employeeId,
        Instant joinedAt,
        Optional<Instant> expiresAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        String statusReason,
        boolean defaultTenant,
        TenantMembershipSource membershipSource,
        long version) {

    private static final Map<MembershipStatus, Set<MembershipStatus>> ALLOWED_TRANSITIONS = transitions();

    public TenantMembership {
        Objects.requireNonNull(membershipId, "membershipId");
        Objects.requireNonNull(tenantId, "tenantId");
        requireUser(userPrincipal);
        Objects.requireNonNull(status, "status");
        employeeId = employeeId == null ? Optional.empty() : employeeId.map(String::trim).filter(v -> !v.isEmpty());
        Objects.requireNonNull(joinedAt, "joinedAt");
        expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        if (expiresAt.isPresent() && !expiresAt.orElseThrow().isAfter(joinedAt)) {
            throw new IllegalArgumentException("expiresAt must be after joinedAt");
        }
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("updatedAt must not precede joinedAt");
        }
        createdBy = OrganizationText.required(createdBy, "createdBy", 128);
        updatedBy = OrganizationText.required(updatedBy, "updatedBy", 128);
        statusReason = OrganizationText.optional(statusReason, 500);
        membershipSource = Objects.requireNonNull(membershipSource, "membershipSource");
        if (defaultTenant && status != MembershipStatus.ACTIVE) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.INVALID_STATUS_TRANSITION,
                    "Only an active Tenant Membership can be the default Tenant");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

    public static TenantMembership create(
            MembershipId membershipId,
            TenantId tenantId,
            PrincipalRef user,
            MembershipStatus initialStatus,
            String employeeId,
            Instant joinedAt,
            Instant expiresAt,
            boolean defaultTenant,
            TenantMembershipSource source,
            String actorId,
            String reason) {
        return new TenantMembership(
                membershipId,
                tenantId,
                user,
                initialStatus,
                Optional.ofNullable(employeeId),
                joinedAt,
                Optional.ofNullable(expiresAt),
                joinedAt,
                actorId,
                actorId,
                reason,
                defaultTenant,
                source,
                1);
    }

    public TenantMembership changeStatus(
            MembershipStatus target,
            String actorId,
            String reason,
            Instant at) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(at, "at");
        String checkedReason = OrganizationText.required(reason, "reason", 500);
        if (target == status) {
            return this;
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.INVALID_STATUS_TRANSITION,
                    "Tenant Membership transition " + status + " -> " + target + " is not allowed");
        }
        if (target == MembershipStatus.ACTIVE && expiresAt.isPresent()
                && !expiresAt.orElseThrow().isAfter(at)) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.INVALID_STATUS_TRANSITION,
                    "An expired Tenant Membership cannot be activated without a future expiry");
        }
        return new TenantMembership(
                membershipId,
                tenantId,
                userPrincipal,
                target,
                employeeId,
                joinedAt,
                expiresAt,
                at,
                createdBy,
                actorId,
                checkedReason,
                target == MembershipStatus.ACTIVE && defaultTenant,
                membershipSource,
                version + 1);
    }

    public TenantMembership readmit(
            String newEmployeeId,
            Instant newExpiresAt,
            boolean newDefaultTenant,
            TenantMembershipSource newSource,
            String actorId,
            String reason,
            Instant at) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(newSource, "newSource");
        if (status != MembershipStatus.REMOVED) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.TENANT_MEMBERSHIP_ALREADY_EXISTS,
                    "Only a removed Tenant Membership can be re-admitted");
        }
        if (newExpiresAt != null && !newExpiresAt.isAfter(at)) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.INVALID_STATUS_TRANSITION,
                    "Re-admitted Tenant Membership requires a future expiry");
        }
        return new TenantMembership(
                membershipId,
                tenantId,
                userPrincipal,
                MembershipStatus.ACTIVE,
                Optional.ofNullable(newEmployeeId),
                joinedAt,
                Optional.ofNullable(newExpiresAt),
                at,
                createdBy,
                actorId,
                OrganizationText.required(reason, "reason", 500),
                newDefaultTenant,
                newSource,
                version + 1);
    }

    public TenantMembership updateDetails(
            String newEmployeeId,
            Instant newExpiresAt,
            boolean newDefaultTenant,
            String actorId,
            String reason,
            Instant at) {
        Objects.requireNonNull(at, "at");
        if (status == MembershipStatus.REMOVED) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.INVALID_STATUS_TRANSITION,
                    "Removed Tenant Memberships cannot be updated");
        }
        if (newDefaultTenant && status != MembershipStatus.ACTIVE) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.INVALID_STATUS_TRANSITION,
                    "Only an active Tenant Membership can be the default Tenant");
        }
        return new TenantMembership(
                membershipId,
                tenantId,
                userPrincipal,
                status,
                Optional.ofNullable(newEmployeeId),
                joinedAt,
                Optional.ofNullable(newExpiresAt),
                at,
                createdBy,
                actorId,
                OrganizationText.required(reason, "reason", 500),
                newDefaultTenant,
                membershipSource,
                version + 1);
    }

    public boolean activeAt(Instant at) {
        return status == MembershipStatus.ACTIVE
                && (expiresAt.isEmpty() || expiresAt.orElseThrow().isAfter(at));
    }

    /**
     * Returns whether this Tenant Membership may receive organization placement before sign-in.
     * INVITED users may be pre-provisioned into Departments and Groups, but effective access
     * remains gated by an ACTIVE Tenant Membership and ACTIVE user identity.
     */
    public boolean organizationAssignableAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return (status == MembershipStatus.INVITED || status == MembershipStatus.ACTIVE)
                && (expiresAt.isEmpty() || expiresAt.orElseThrow().isAfter(at));
    }

    private static void requireUser(PrincipalRef principal) {
        Objects.requireNonNull(principal, "userPrincipal");
        if (principal.principalType() != PrincipalRef.PrincipalType.USER) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.PRINCIPAL_TYPE_UNSUPPORTED,
                    "Tenant membership requires a USER principal");
        }
    }

    private static Map<MembershipStatus, Set<MembershipStatus>> transitions() {
        EnumMap<MembershipStatus, Set<MembershipStatus>> result = new EnumMap<>(MembershipStatus.class);
        result.put(MembershipStatus.INVITED, EnumSet.of(
                MembershipStatus.ACTIVE,
                MembershipStatus.SUSPENDED,
                MembershipStatus.EXPIRED,
                MembershipStatus.REMOVED));
        result.put(MembershipStatus.ACTIVE, EnumSet.of(
                MembershipStatus.SUSPENDED,
                MembershipStatus.EXPIRED,
                MembershipStatus.REMOVED));
        result.put(MembershipStatus.SUSPENDED, EnumSet.of(
                MembershipStatus.ACTIVE,
                MembershipStatus.EXPIRED,
                MembershipStatus.REMOVED));
        result.put(MembershipStatus.EXPIRED, EnumSet.of(
                MembershipStatus.ACTIVE,
                MembershipStatus.REMOVED));
        result.put(MembershipStatus.REMOVED, EnumSet.noneOf(MembershipStatus.class));
        return Map.copyOf(result);
    }
}
