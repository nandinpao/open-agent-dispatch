package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Phase 1 task-level semantic requirement.
 *
 * <p>This is the canonical WHAT contract used by later delegation and planning work.
 * It deliberately contains no targetDomainId, targetSystemId, targetAgentPoolId or
 * targetAgentId. Those are never required delegation intent fields.</p>
 */
public record CapabilityRequirement(
        String capabilityCode,
        String operation,
        Map<String, Object> inputContext,
        Map<String, Object> resourceConstraints,
        String dataClassification,
        String requiredAssurance,
        OffsetDateTime deadline,
        String qualityPreference) {

    public CapabilityRequirement {
        inputContext = inputContext == null ? Map.of() : Map.copyOf(inputContext);
        resourceConstraints = resourceConstraints == null ? Map.of() : Map.copyOf(resourceConstraints);
    }
}
