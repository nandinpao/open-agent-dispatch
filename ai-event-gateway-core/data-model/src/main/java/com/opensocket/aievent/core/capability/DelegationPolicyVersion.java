package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Append-only Phase 3 WHO MAY policy snapshot.
 *
 * <p>This evidence allows an authorization decision's selected policy/version to be reconstructed
 * without treating historical policy state as current routing or execution authority.</p>
 */
public record DelegationPolicyVersion(
        String tenantId,
        String policyId,
        int version,
        Map<String, Object> snapshot,
        String changeReason,
        String actorRef,
        OffsetDateTime createdAt) {

    public DelegationPolicyVersion {
        snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
    }
}
