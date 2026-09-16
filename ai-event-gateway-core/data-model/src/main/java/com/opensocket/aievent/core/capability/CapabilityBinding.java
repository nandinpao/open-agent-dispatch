package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 2 declaration/evidence that a Provider CAN provide a Canonical Capability.
 *
 * <p>APPROVED is a catalog trust state only. A binding never means WHO MAY execute,
 * never selects WHO SHOULD execute, and never chooses HOW execution is transported.</p>
 */
public record CapabilityBinding(
        String tenantId,
        String bindingId,
        String capabilityCode,
        String providerId,
        String providerType,
        String providerDisplayName,
        List<String> supportedOperations,
        String trustStatus,
        String sourceRevision,
        String verificationMethod,
        String verificationEvidenceRef,
        OffsetDateTime observedAt,
        OffsetDateTime verifiedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime staleAfter,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public CapabilityBinding {
        supportedOperations = supportedOperations == null ? List.of() : List.copyOf(supportedOperations);
    }
}
