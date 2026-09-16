package com.opensocket.aievent.core.iam.organization.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Department {
    private final TenantId tenantId; private final DepartmentId departmentId; private final String code;
    private final String name; private final Optional<DepartmentId> parentDepartmentId;
    private final Optional<PrincipalRef> manager; private final DepartmentStatus status; private final int displayOrder;
    private final Instant createdAt; private final Instant updatedAt; private final String updatedBy; private final long version;

    private Department(TenantId tenantId, DepartmentId departmentId, String code, String name,
                       Optional<DepartmentId> parentDepartmentId, Optional<PrincipalRef> manager, DepartmentStatus status,
                       int displayOrder, Instant createdAt, Instant updatedAt, String updatedBy, long version) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId"); this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
        this.code = OrganizationText.required(code, "departmentCode", 64); this.name = OrganizationText.required(name, "departmentName", 200);
        this.parentDepartmentId = parentDepartmentId == null ? Optional.empty() : parentDepartmentId;
        if (this.parentDepartmentId.filter(departmentId::equals).isPresent()) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_CYCLE_DETECTED, "Department cannot be its own parent");
        this.manager = manager == null ? Optional.empty() : manager;
        this.manager.ifPresent(p -> { if (p.principalType() != PrincipalRef.PrincipalType.USER) throw new OrganizationDomainException(OrganizationReasonCode.PRINCIPAL_TYPE_UNSUPPORTED, "Department manager must be a USER"); });
        this.status = Objects.requireNonNull(status, "status");
        if (displayOrder < 0) throw new IllegalArgumentException("displayOrder must be non-negative"); this.displayOrder = displayOrder;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt"); this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        this.updatedBy = OrganizationText.required(updatedBy, "updatedBy", 128); if (version < 1) throw new IllegalArgumentException("version must be positive"); this.version = version;
    }

    public static Department create(TenantId tenantId, DepartmentId id, String code, String name, Optional<DepartmentId> parent,
                                    Optional<PrincipalRef> manager, int displayOrder, String actorId, Instant at) {
        return new Department(tenantId, id, code, name, parent, manager, DepartmentStatus.ACTIVE, displayOrder, at, at, actorId, 1);
    }

    public static Department reconstitute(TenantId tenantId, DepartmentId id, String code, String name, Optional<DepartmentId> parent,
                                          Optional<PrincipalRef> manager, DepartmentStatus status, int displayOrder, Instant createdAt,
                                          Instant updatedAt, String updatedBy, long version) {
        return new Department(tenantId, id, code, name, parent, manager, status, displayOrder, createdAt, updatedAt, updatedBy, version);
    }

    public Department rename(String newName, String actorId, Instant at) { return copy(code, newName, parentDepartmentId, manager, status, displayOrder, actorId, at); }
    public Department changeCode(String newCode, String actorId, Instant at) { return copy(newCode, name, parentDepartmentId, manager, status, displayOrder, actorId, at); }
    public Department moveTo(Optional<DepartmentId> newParent, String actorId, Instant at) { return copy(code, name, newParent, manager, status, displayOrder, actorId, at); }
    public Department disable(String actorId, Instant at) { return copy(code, name, parentDepartmentId, manager, DepartmentStatus.DISABLED, displayOrder, actorId, at); }
    public Department reactivate(String actorId, Instant at) { return copy(code, name, parentDepartmentId, manager, DepartmentStatus.ACTIVE, displayOrder, actorId, at); }
    public Department assignManager(Optional<PrincipalRef> newManager, String actorId, Instant at) { return copy(code, name, parentDepartmentId, newManager, status, displayOrder, actorId, at); }
    public Department update(String newCode,String newName,Optional<DepartmentId> newParent,Optional<PrincipalRef> newManager,int newDisplayOrder,String actorId,Instant at){return copy(newCode,newName,newParent,newManager,status,newDisplayOrder,actorId,at);}
    public Department changeStatus(DepartmentStatus target,String actorId,Instant at){return copy(code,name,parentDepartmentId,manager,target,displayOrder,actorId,at);}

    private Department copy(String c, String n, Optional<DepartmentId> p, Optional<PrincipalRef> m, DepartmentStatus s, int order, String actor, Instant at) {
        return new Department(tenantId, departmentId, c, n, p, m, s, order, createdAt, at, actor, version + 1);
    }

    public TenantId tenantId() { return tenantId; } public DepartmentId departmentId() { return departmentId; }
    public String code() { return code; } public String name() { return name; } public Optional<DepartmentId> parentDepartmentId() { return parentDepartmentId; }
    public Optional<PrincipalRef> manager() { return manager; } public DepartmentStatus status() { return status; } public int displayOrder() { return displayOrder; }
    public Instant createdAt() { return createdAt; } public Instant updatedAt() { return updatedAt; } public String updatedBy() { return updatedBy; } public long version() { return version; }
}
