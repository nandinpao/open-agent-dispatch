package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Immutable Phase 5 execution-adapter configuration evidence. */
public record ExecutionAdapterVersion(String tenantId, String adapterId, Integer version, Map<String,Object> snapshot, String changeReason, String actorRef, OffsetDateTime createdAt) {
    public ExecutionAdapterVersion { snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot); }
}
