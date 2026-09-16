package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Immutable late/stale result evidence. Payload content is never stored, only a hash. */
public record RuntimeLateResultQuarantine(
        String tenantId,
        String quarantineId,
        RuntimeResultSubmission submission,
        RuntimeLeaseStatus leaseStatus,
        String reasonCode,
        RuntimeLateResultQuarantineStatus status,
        String resolvedBy,
        String resolutionReason,
        Instant quarantinedAt,
        Instant resolvedAt,
        long version) {
    public RuntimeLateResultQuarantine {
        tenantId=required(tenantId,"tenantId"); quarantineId=required(quarantineId,"quarantineId");
        Objects.requireNonNull(submission,"submission"); if(!tenantId.equals(submission.tenantId()))throw new IllegalArgumentException("tenant mismatch");
        Objects.requireNonNull(leaseStatus,"leaseStatus"); reasonCode=required(reasonCode,"reasonCode"); Objects.requireNonNull(status,"status");
        resolvedBy=resolvedBy==null?"":resolvedBy.trim(); resolutionReason=resolutionReason==null?"":resolutionReason.trim();
        Objects.requireNonNull(quarantinedAt,"quarantinedAt"); if(version<0)throw new IllegalArgumentException("version must be non-negative");
        if(status!=RuntimeLateResultQuarantineStatus.OPEN&&resolvedAt==null)throw new IllegalArgumentException("resolved quarantine requires resolvedAt");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
