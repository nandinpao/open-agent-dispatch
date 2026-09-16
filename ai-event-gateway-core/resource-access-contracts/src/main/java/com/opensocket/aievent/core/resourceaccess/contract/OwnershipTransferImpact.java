package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.List;
import java.util.Objects;

/** Explainable preview produced before an ownership mutation is allowed. */
public record OwnershipTransferImpact(
        ResourceRef resourceRef,
        OwnershipDescriptor currentOwnership,
        OwnershipDescriptor proposedOwnership,
        long affectedResourceCount,
        long affectedParticipantCount,
        List<String> warnings,
        long expectedResourceVersion) {
    public OwnershipTransferImpact {
        Objects.requireNonNull(resourceRef, "resourceRef");
        Objects.requireNonNull(currentOwnership, "currentOwnership");
        Objects.requireNonNull(proposedOwnership, "proposedOwnership");
        if (affectedResourceCount < 1 || affectedParticipantCount < 0 || expectedResourceVersion < 1)
            throw new IllegalArgumentException("invalid ownership impact counters");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
