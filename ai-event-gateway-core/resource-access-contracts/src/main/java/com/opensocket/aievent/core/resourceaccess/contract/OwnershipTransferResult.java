package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record OwnershipTransferResult(
        ResourceRef resourceRef,
        OwnershipDescriptor previousOwnership,
        OwnershipDescriptor currentOwnership,
        long resourceVersion,
        String sourceEventId,
        String actorId,
        String reason,
        Instant transferredAt) {
    public OwnershipTransferResult {
        Objects.requireNonNull(resourceRef, "resourceRef");
        Objects.requireNonNull(previousOwnership, "previousOwnership");
        Objects.requireNonNull(currentOwnership, "currentOwnership");
        if (resourceVersion < 1) throw new IllegalArgumentException("resourceVersion must be positive");
        if (sourceEventId == null || sourceEventId.isBlank()) throw new IllegalArgumentException("sourceEventId is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        Objects.requireNonNull(transferredAt, "transferredAt");
    }
}
