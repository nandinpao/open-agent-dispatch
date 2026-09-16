package com.opensocket.aievent.core.iam.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Group {
    private final TenantId tenantId; private final GroupId groupId; private final String code; private final String name;
    private final GroupType type; private final Optional<GroupId> parentGroupId; private final Optional<DepartmentId> ownerDepartmentId;
    private final GroupStatus status; private final String description; private final Instant createdAt; private final Instant updatedAt; private final String updatedBy; private final long version;

    private Group(TenantId tenantId, GroupId groupId, String code, String name, GroupType type, Optional<GroupId> parentGroupId,
                  Optional<DepartmentId> ownerDepartmentId, GroupStatus status, String description, Instant createdAt,
                  Instant updatedAt, String updatedBy, long version) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId"); this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.code = OrganizationText.required(code, "groupCode", 64); this.name = OrganizationText.required(name, "groupName", 200); this.type = Objects.requireNonNull(type, "type");
        this.parentGroupId = parentGroupId == null ? Optional.empty() : parentGroupId; if (this.parentGroupId.filter(groupId::equals).isPresent()) throw new IllegalArgumentException("group cannot be its own parent");
        this.ownerDepartmentId = ownerDepartmentId == null ? Optional.empty() : ownerDepartmentId; this.status = Objects.requireNonNull(status, "status");
        this.description = OrganizationText.optional(description, 1000); this.createdAt = Objects.requireNonNull(createdAt, "createdAt"); this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.updatedBy = OrganizationText.required(updatedBy, "updatedBy", 128); if (version < 1) throw new IllegalArgumentException("version must be positive"); this.version = version;
    }

    public static Group create(TenantId tenantId, GroupId id, String code, String name, GroupType type, Optional<GroupId> parent, Optional<DepartmentId> owner, String description, String actorId, Instant at) {
        return new Group(tenantId, id, code, name, type, parent, owner, GroupStatus.ACTIVE, description, at, at, actorId, 1);
    }
    public static Group reconstitute(TenantId tenantId, GroupId id, String code, String name, GroupType type, Optional<GroupId> parent, Optional<DepartmentId> owner, GroupStatus status, String description, Instant createdAt, Instant updatedAt, String updatedBy, long version) {
        return new Group(tenantId, id, code, name, type, parent, owner, status, description, createdAt, updatedAt, updatedBy, version);
    }
    public Group disable(String actor, Instant at) { return copy(code,name,type,parentGroupId,ownerDepartmentId,GroupStatus.DISABLED,description,actor,at); } public Group reactivate(String actor, Instant at) { return copy(code,name,type,parentGroupId,ownerDepartmentId,GroupStatus.ACTIVE,description,actor,at); }
    public Group update(String newCode,String newName,GroupType newType,Optional<GroupId> newParent,Optional<DepartmentId> newOwner,String newDescription,String actor,Instant at){return copy(newCode,newName,newType,newParent,newOwner,status,newDescription,actor,at);}
    public Group changeStatus(GroupStatus target,String actor,Instant at){return copy(code,name,type,parentGroupId,ownerDepartmentId,target,description,actor,at);}
    private Group copy(String c,String n,GroupType t,Optional<GroupId> p,Optional<DepartmentId> o,GroupStatus s,String d,String actor,Instant at) { return new Group(tenantId, groupId, c, n, t, p, o, s, d, createdAt, at, actor, version + 1); }
    public TenantId tenantId() { return tenantId; } public GroupId groupId() { return groupId; } public String code() { return code; } public String name() { return name; } public GroupType type() { return type; } public Optional<GroupId> parentGroupId() { return parentGroupId; } public Optional<DepartmentId> ownerDepartmentId() { return ownerDepartmentId; } public GroupStatus status() { return status; } public String description() { return description; } public Instant createdAt() { return createdAt; } public Instant updatedAt() { return updatedAt; } public String updatedBy() { return updatedBy; } public long version() { return version; }
}
