package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Maps an already-known enterprise Service Code classification to a semantic capability.
 * The mapping describes WHAT is required; it never names a provider or execution target.
 */
public record ServiceCodeCapabilityMapping(
        String tenantId,
        String serviceCode,
        String capabilityCode,
        Map<String, Object> requirementDefaults,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public ServiceCodeCapabilityMapping {
        requirementDefaults = requirementDefaults == null ? Map.of() : Map.copyOf(requirementDefaults);
    }
}
