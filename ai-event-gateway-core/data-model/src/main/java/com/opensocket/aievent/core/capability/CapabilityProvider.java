package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Phase 2 provider identity used only to answer WHO CAN.
 *
 * <p>The provider reference is opaque. This contract deliberately contains no endpoint,
 * credential, authorization result, routing score, selected state, Agent Pool or transport.
 * Those concerns belong to later governance/routing/execution phases.</p>
 */
public record CapabilityProvider(
        String tenantId,
        String providerId,
        String providerType,
        String displayName,
        String providerRef,
        String registrationSource,
        String catalogStatus,
        Map<String, Object> metadata,
        OffsetDateTime observedAt,
        OffsetDateTime lastVerifiedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public CapabilityProvider {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
