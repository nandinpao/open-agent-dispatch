package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Immutable SQL-ready scope plan materialized for one exact policy/epoch/department-revision namespace. */
public record MaterializedScopeSnapshot(
        String snapshotId,
        String tenantId,
        ScopeSnapshotKind snapshotKind,
        String principalType,
        String principalId,
        String permissionCode,
        ResourceType resourceType,
        String strategy,
        Set<String> exactDepartmentIds,
        Set<String> subtreeDepartmentRootIds,
        Set<String> groupIds,
        Set<String> explicitResourceIds,
        Set<String> excludedResourceIds,
        Set<String> deniedDepartmentIds,
        Set<String> deniedSubtreeDepartmentRootIds,
        Set<String> deniedGroupIds,
        VisibilityLevel maximumVisibility,
        PolicyVersion policyVersion,
        SecurityEpoch securityEpoch,
        long departmentRevision,
        String planHash,
        MaterializedScopeSnapshotStatus status,
        Instant preparedAt,
        Instant activatedAt,
        Instant retiredAt,
        long version) {
    public MaterializedScopeSnapshot {
        snapshotId = required(snapshotId,"snapshotId"); tenantId = required(tenantId,"tenantId");
        Objects.requireNonNull(snapshotKind,"snapshotKind"); principalType=required(principalType,"principalType");
        principalId=required(principalId,"principalId"); permissionCode=required(permissionCode,"permissionCode");
        Objects.requireNonNull(resourceType,"resourceType"); strategy=required(strategy,"strategy");
        exactDepartmentIds=copy(exactDepartmentIds); subtreeDepartmentRootIds=copy(subtreeDepartmentRootIds);
        groupIds=copy(groupIds); explicitResourceIds=copy(explicitResourceIds); excludedResourceIds=copy(excludedResourceIds);
        deniedDepartmentIds=copy(deniedDepartmentIds); deniedSubtreeDepartmentRootIds=copy(deniedSubtreeDepartmentRootIds);
        deniedGroupIds=copy(deniedGroupIds); maximumVisibility=maximumVisibility==null?VisibilityLevel.NONE:maximumVisibility;
        policyVersion=policyVersion==null?PolicyVersion.ZERO:policyVersion; securityEpoch=securityEpoch==null?SecurityEpoch.ZERO:securityEpoch;
        if(departmentRevision<0||version<0)throw new IllegalArgumentException("snapshot revisions must be non-negative");
        if(departmentRevision!=securityEpoch.departmentTreeRevision())throw new IllegalArgumentException("department revision must match security epoch");
        planHash=required(planHash,"planHash"); Objects.requireNonNull(status,"status"); Objects.requireNonNull(preparedAt,"preparedAt");
        if(status==MaterializedScopeSnapshotStatus.ACTIVE&&activatedAt==null)throw new IllegalArgumentException("active snapshot requires activatedAt");
    }
    private static Set<String> copy(Set<String> v){return v==null?Set.of():Set.copyOf(v);}    
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
