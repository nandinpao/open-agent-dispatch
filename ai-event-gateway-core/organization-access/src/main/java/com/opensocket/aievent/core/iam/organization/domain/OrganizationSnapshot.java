package com.opensocket.aievent.core.iam.organization.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable AS_RECORDED organization semantics captured by business resources and audit. */
public record OrganizationSnapshot(SnapshotId snapshotId, TenantId tenantId, DepartmentId departmentId,
                                   long departmentRevision, String departmentCode, String departmentName,
                                   List<DepartmentId> ancestorPathIds, List<String> ancestorPathCodes,
                                   List<String> ancestorPathNames, Set<GroupId> groupIds, Instant capturedAt,
                                   String contentHash) {
    public OrganizationSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId"); Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(departmentId, "departmentId"); if (departmentRevision < 1) throw new IllegalArgumentException("departmentRevision must be positive");
        departmentCode = OrganizationText.required(departmentCode, "departmentCode", 64); departmentName = OrganizationText.required(departmentName, "departmentName", 200);
        ancestorPathIds = ancestorPathIds == null ? List.of() : List.copyOf(ancestorPathIds); ancestorPathCodes = ancestorPathCodes == null ? List.of() : List.copyOf(ancestorPathCodes); ancestorPathNames = ancestorPathNames == null ? List.of() : List.copyOf(ancestorPathNames);
        if (ancestorPathIds.size() != ancestorPathCodes.size() || ancestorPathIds.size() != ancestorPathNames.size()) throw new IllegalArgumentException("ancestor path lists must have equal size");
        groupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds); Objects.requireNonNull(capturedAt, "capturedAt");
        contentHash = OrganizationText.required(contentHash, "contentHash", 128);
    }
}
