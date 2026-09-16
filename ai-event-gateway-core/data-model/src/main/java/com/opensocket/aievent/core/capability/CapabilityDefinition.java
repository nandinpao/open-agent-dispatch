package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 canonical enterprise semantic capability.
 *
 * <p>This object describes WHAT the enterprise can do. It intentionally carries no
 * target system, target domain, Agent, Agent Pool, endpoint, credential or protocol.
 * Provider discovery and execution bindings are introduced in later phases.</p>
 */
public record CapabilityDefinition(
        String tenantId,
        String capabilityId,
        String capabilityCode,
        String displayName,
        String description,
        String semanticDomain,
        String category,
        String capabilityType,
        List<String> operations,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        List<String> resourceTypes,
        List<String> dataClasses,
        int version,
        String status,
        List<String> serviceCodes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public CapabilityDefinition {
        operations = operations == null ? List.of() : List.copyOf(operations);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        resourceTypes = resourceTypes == null ? List.of() : List.copyOf(resourceTypes);
        dataClasses = dataClasses == null ? List.of() : List.copyOf(dataClasses);
        serviceCodes = serviceCodes == null ? List.of() : List.copyOf(serviceCodes);
    }
}
