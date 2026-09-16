package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Immutable Phase 6 Triage Policy snapshot. */
public record TriagePolicyVersion(
        String tenantId,
        String policyId,
        int version,
        Map<String, Object> snapshot,
        String changeReason,
        String actorRef,
        OffsetDateTime createdAt) {
    public TriagePolicyVersion { snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot); }
}
