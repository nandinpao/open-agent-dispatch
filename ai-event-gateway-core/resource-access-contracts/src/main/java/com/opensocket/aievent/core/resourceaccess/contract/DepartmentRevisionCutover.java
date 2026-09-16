package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Tenant-scoped cutover evidence for activating a prepared materialized-scope revision. */
public record DepartmentRevisionCutover(
        String tenantId,
        String cutoverId,
        long departmentRevision,
        DepartmentRevisionCutoverStatus status,
        long preparedSnapshotCount,
        String preparedBy,
        String activatedBy,
        String correlationId,
        Instant preparedAt,
        Instant activatedAt,
        long version) {
    public DepartmentRevisionCutover {
        tenantId=required(tenantId,"tenantId"); cutoverId=required(cutoverId,"cutoverId");
        if(departmentRevision<0||preparedSnapshotCount<0||version<0)throw new IllegalArgumentException("cutover values must be non-negative");
        Objects.requireNonNull(status,"status"); preparedBy=required(preparedBy,"preparedBy");
        activatedBy=activatedBy==null?"":activatedBy.trim(); correlationId=required(correlationId,"correlationId");
        Objects.requireNonNull(preparedAt,"preparedAt");
        if(status==DepartmentRevisionCutoverStatus.ACTIVE&&activatedAt==null)throw new IllegalArgumentException("active cutover requires activatedAt");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
