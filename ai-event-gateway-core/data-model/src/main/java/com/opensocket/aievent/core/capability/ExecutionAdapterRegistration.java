package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Phase 5 HOW configuration. It is selected only after a persisted Phase 4 WHO SHOULD decision. */
public record ExecutionAdapterRegistration(
        String tenantId, String adapterId, String providerId, String providerType, String adapterType,
        String protocol, String protocolVersion, String endpointRef, String credentialRef, String runtimeRef,
        String status, Integer selectionPriority, Map<String,Object> configuration, Integer version,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public ExecutionAdapterRegistration { configuration = configuration == null ? Map.of() : Map.copyOf(configuration); }
}
