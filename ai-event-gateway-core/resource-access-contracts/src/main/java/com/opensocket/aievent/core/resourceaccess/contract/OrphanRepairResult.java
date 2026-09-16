package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record OrphanRepairResult(String repairId, ResourceRef resourceRef, OwnershipDescriptor ownership,
        long resourceVersion, String status, Instant completedAt) {
    public OrphanRepairResult {
        if (repairId == null || repairId.isBlank()) throw new IllegalArgumentException("repairId is required");
        Objects.requireNonNull(resourceRef, "resourceRef"); Objects.requireNonNull(ownership, "ownership");
        if (resourceVersion < 1) throw new IllegalArgumentException("resourceVersion must be positive");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
        Objects.requireNonNull(completedAt, "completedAt");
    }
}
