package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Immutable Phase 4 Routing Profile snapshot used to reconstruct WHO SHOULD decisions. */
public record RoutingProfileVersion(
        String tenantId,
        String profileId,
        int version,
        Map<String, Object> snapshot,
        String changeReason,
        String actorRef,
        OffsetDateTime createdAt) {
    public RoutingProfileVersion {
        snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
    }
}
