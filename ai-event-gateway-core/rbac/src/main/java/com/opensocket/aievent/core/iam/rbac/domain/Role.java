package com.opensocket.aievent.core.iam.rbac.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Role {
    private final RoleId roleId;
    private final Optional<String> tenantId;
    private final RoleCode roleCode;
    private final String roleName;
    private final String description;
    private final RoleType roleType;
    private final RoleStatus status;
    private final boolean systemManaged;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;
    private final long version;

    private Role(RoleId id, Optional<String> tenant, RoleCode code, String name, String description,
                 RoleType type, RoleStatus status, boolean systemManaged, Instant createdAt,
                 Instant updatedAt, String createdBy, String updatedBy, long version) {
        this.roleId = Objects.requireNonNull(id, "roleId");
        this.tenantId = tenant == null ? Optional.empty() : tenant.map(String::trim).filter(v -> !v.isBlank());
        this.roleCode = Objects.requireNonNull(code, "roleCode");
        this.roleName = required(name, "roleName", 200);
        this.description = description == null ? "" : description.trim();
        this.roleType = Objects.requireNonNull(type, "roleType");
        this.status = Objects.requireNonNull(status, "status");
        this.systemManaged = systemManaged;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.createdBy = required(createdBy, "createdBy", 128);
        this.updatedBy = required(updatedBy, "updatedBy", 128);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
        validateOwnership();
    }

    public static Role customTenantRole(RoleId id, String tenantId, RoleCode code, String name,
                                        String description, String actor, Instant at) {
        return new Role(id, Optional.of(required(tenantId, "tenantId", 64)), code, name, description,
                RoleType.CUSTOM_TENANT_ROLE, RoleStatus.ACTIVE, false, at, at, actor, actor, 1);
    }

    public static Role customPlatformRole(RoleId id, RoleCode code, String name, String description,
                                          String actor, Instant at) {
        return new Role(id, Optional.empty(), code, name, description, RoleType.CUSTOM_PLATFORM_ROLE,
                RoleStatus.ACTIVE, false, at, at, actor, actor, 1);
    }

    public static Role reconstitute(RoleId id, Optional<String> tenantId, RoleCode code, String name,
                                    String description, RoleType type, RoleStatus status,
                                    boolean systemManaged, Instant createdAt, Instant updatedAt,
                                    String createdBy, String updatedBy, long version) {
        return new Role(id, tenantId, code, name, description, type, status, systemManaged,
                createdAt, updatedAt, createdBy, updatedBy, version);
    }

    public Role update(String name, String description, String actor, Instant at) {
        ensureMutable();
        return new Role(roleId, tenantId, roleCode, name, description, roleType, status, false,
                createdAt, at, createdBy, actor, version + 1);
    }

    public Role changeStatus(RoleStatus next, String actor, Instant at) {
        Objects.requireNonNull(next, "next");
        if (systemManaged) throw new RbacDomainException(RbacReasonCode.ROLE_SYSTEM_MANAGED, "System-managed roles cannot be modified at runtime");
        if (status == RoleStatus.DELETED && next != RoleStatus.DELETED) {
            throw new RbacDomainException(RbacReasonCode.ROLE_STATUS_TRANSITION_INVALID, "Deleted responsibilities cannot be reactivated");
        }
        if (next == status) return this;
        return new Role(roleId, tenantId, roleCode, roleName, description, roleType, next, false,
                createdAt, at, createdBy, actor, version + 1);
    }

    public boolean platformOwned() {
        return roleType == RoleType.SYSTEM_ROLE || roleType == RoleType.CUSTOM_PLATFORM_ROLE;
    }

    public boolean tenantAssignable() {
        return roleType == RoleType.TENANT_ROLE || roleType == RoleType.CUSTOM_TENANT_ROLE
                || roleType == RoleType.LEGACY_COMPATIBILITY_ROLE;
    }

    private void ensureMutable() {
        if (status == RoleStatus.DELETED) {
            throw new RbacDomainException(RbacReasonCode.ROLE_STATUS_TRANSITION_INVALID, "Deleted responsibilities are read-only tombstones retained for audit");
        }
        if (systemManaged) {
            throw new RbacDomainException(RbacReasonCode.ROLE_SYSTEM_MANAGED,
                    "System-managed roles cannot be modified at runtime");
        }
    }

    private void validateOwnership() {
        boolean tenantOwned = tenantId.isPresent();
        switch (roleType) {
            case CUSTOM_TENANT_ROLE -> {
                if (!tenantOwned || systemManaged) throw new IllegalArgumentException(
                        "custom tenant role must be tenant-owned and mutable");
            }
            case CUSTOM_PLATFORM_ROLE -> {
                if (tenantOwned || systemManaged) throw new IllegalArgumentException(
                        "custom platform role must be instance-owned and mutable");
            }
            default -> {
                if (tenantOwned || !systemManaged) throw new IllegalArgumentException(
                        "system/template roles must be instance-owned and system-managed");
            }
        }
    }

    public RoleId roleId() { return roleId; }
    public Optional<String> tenantId() { return tenantId; }
    public RoleCode roleCode() { return roleCode; }
    public String roleName() { return roleName; }
    public String description() { return description; }
    public RoleType roleType() { return roleType; }
    public RoleStatus status() { return status; }
    public boolean systemManaged() { return systemManaged; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public long version() { return version; }
    public boolean active() { return status == RoleStatus.ACTIVE; }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return normalized;
    }
}
