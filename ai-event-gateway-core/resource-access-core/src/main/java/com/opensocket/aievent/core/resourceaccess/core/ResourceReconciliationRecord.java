package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.time.Instant;
import java.util.Objects;

public record ResourceReconciliationRecord(String reconciliationId, ResourceRef resourceRef,
        ResourceReconciliationStatus status, String projectedHash, String authorityHash,
        String reasonCode, boolean autoRepaired, Instant reconciledAt) {
    public ResourceReconciliationRecord {
        if (reconciliationId == null || reconciliationId.isBlank()) throw new IllegalArgumentException("reconciliationId is required");
        Objects.requireNonNull(resourceRef, "resourceRef"); Objects.requireNonNull(status, "status");
        projectedHash = projectedHash == null ? "" : projectedHash; authorityHash = authorityHash == null ? "" : authorityHash;
        reasonCode = reasonCode == null ? "" : reasonCode.trim(); Objects.requireNonNull(reconciledAt, "reconciledAt");
    }
}
