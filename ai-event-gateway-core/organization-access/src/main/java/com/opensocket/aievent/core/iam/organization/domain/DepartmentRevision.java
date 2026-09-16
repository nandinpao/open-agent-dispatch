package com.opensocket.aievent.core.iam.organization.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DepartmentRevision(TenantId tenantId, DepartmentId departmentId, long revision, String departmentCode,
                                 String departmentName, Optional<DepartmentId> parentDepartmentId,
                                 List<DepartmentId> ancestorPathIds, List<String> ancestorPathCodes,
                                 List<String> ancestorPathNames, Instant validFrom, Optional<Instant> validTo,
                                 DepartmentRevisionChangeType changeType, String changedBy, String changeReason,
                                 Instant createdAt) {
    public DepartmentRevision {
        Objects.requireNonNull(tenantId, "tenantId"); Objects.requireNonNull(departmentId, "departmentId");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        departmentCode = OrganizationText.required(departmentCode, "departmentCode", 64);
        departmentName = OrganizationText.required(departmentName, "departmentName", 200);
        parentDepartmentId = parentDepartmentId == null ? Optional.empty() : parentDepartmentId;
        ancestorPathIds = ancestorPathIds == null ? List.of() : List.copyOf(ancestorPathIds);
        ancestorPathCodes = ancestorPathCodes == null ? List.of() : List.copyOf(ancestorPathCodes);
        ancestorPathNames = ancestorPathNames == null ? List.of() : List.copyOf(ancestorPathNames);
        if (ancestorPathIds.size() != ancestorPathCodes.size() || ancestorPathIds.size() != ancestorPathNames.size()) throw new IllegalArgumentException("ancestor path lists must have equal size");
        Objects.requireNonNull(validFrom, "validFrom"); validTo = validTo == null ? Optional.empty() : validTo;
        if (validTo.isPresent() && !validTo.orElseThrow().isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo must be after validFrom");
        }
        Objects.requireNonNull(changeType, "changeType"); changedBy = OrganizationText.required(changedBy, "changedBy", 128);
        changeReason = OrganizationText.required(changeReason, "changeReason", 500); Objects.requireNonNull(createdAt, "createdAt");
    }

    public DepartmentRevision closeAt(Instant at) {
        if (validTo.isPresent()) throw new IllegalStateException("revision is already closed");
        return new DepartmentRevision(tenantId, departmentId, revision, departmentCode, departmentName, parentDepartmentId,
                ancestorPathIds, ancestorPathCodes, ancestorPathNames, validFrom, Optional.of(at), changeType, changedBy, changeReason, createdAt);
    }
}
