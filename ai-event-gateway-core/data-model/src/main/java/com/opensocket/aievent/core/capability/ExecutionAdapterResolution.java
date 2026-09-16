package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Append-only Phase 5 HOW evidence. No secret material is stored here. */
public record ExecutionAdapterResolution(
        String resolutionId, String tenantId, String resolutionMode, String result, String routingDecisionId,
        String capabilityCode, String operation, String bindingId, String providerId, String providerType,
        String adapterId, Integer adapterVersion, String adapterType, String protocol, String protocolVersion,
        String endpointRef, String credentialRef, String runtimeRef, List<String> reasonCodes, OffsetDateTime resolvedAt) {
    public ExecutionAdapterResolution { reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes); }
}
