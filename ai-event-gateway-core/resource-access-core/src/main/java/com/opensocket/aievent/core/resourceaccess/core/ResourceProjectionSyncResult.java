package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.time.Instant;
import java.util.Objects;

public record ResourceProjectionSyncResult(ResourceRef resourceRef, boolean changed, boolean orphaned,
        String previousDescriptorHash, String currentDescriptorHash, long resourceVersion,
        long participantVersion, Instant projectedAt) {
    public ResourceProjectionSyncResult {
        Objects.requireNonNull(resourceRef, "resourceRef");
        previousDescriptorHash = previousDescriptorHash == null ? "" : previousDescriptorHash;
        currentDescriptorHash = currentDescriptorHash == null ? "" : currentDescriptorHash;
        if (resourceVersion < 0 || participantVersion < 0) throw new IllegalArgumentException("projection versions must be non-negative");
        Objects.requireNonNull(projectedAt, "projectedAt");
    }
}
