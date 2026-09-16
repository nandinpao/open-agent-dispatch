package com.opensocket.aievent.core.uicapability.contract;

import java.time.Instant;
import java.util.List;

/**
 * Minimal list-row projection. It intentionally excludes permission codes, roles,
 * scope grants, deny evidence and resource payloads.
 */
public record UiListCapabilitySummary(
        String contextId,
        String resourceRefHash,
        Long resourceVersion,
        long principalEpoch,
        long catalogRevision,
        long policyVersion,
        List<UiCapability> actions,
        Instant expiresAt) {
    public UiListCapabilitySummary {
        if (contextId == null || contextId.isBlank()) throw new IllegalArgumentException("contextId is required");
        resourceRefHash = resourceRefHash == null ? "" : resourceRefHash;
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt is required");
    }
}
