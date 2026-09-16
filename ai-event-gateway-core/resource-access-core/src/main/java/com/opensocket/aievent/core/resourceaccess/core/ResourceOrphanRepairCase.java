package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.OwnershipDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.time.Instant;
import java.util.Objects;

public record ResourceOrphanRepairCase(String repairId, ResourceRef resourceRef, ResourceOrphanRepairStatus status,
        String reasonCode, OwnershipDescriptor repairedOwnership, String assignedTo,
        Instant detectedAt, Instant updatedAt, long version) {
    public ResourceOrphanRepairCase {
        if (repairId == null || repairId.isBlank()) throw new IllegalArgumentException("repairId is required");
        Objects.requireNonNull(resourceRef, "resourceRef"); Objects.requireNonNull(status, "status");
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
        repairedOwnership = repairedOwnership == null ? OwnershipDescriptor.unowned(0) : repairedOwnership;
        assignedTo = assignedTo == null ? "" : assignedTo.trim();
        Objects.requireNonNull(detectedAt, "detectedAt"); Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }
}
