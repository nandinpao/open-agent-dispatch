package com.opensocket.aievent.core.runtime.capability;

import java.time.Instant;

/** Canonical runtime capability contract after legacy browser authentication removal. */
public record RuntimeCapabilitySnapshot(
        String contractVersion,
        Instant generatedAt,
        AuthenticationCapability authentication,
        RuntimeSurfaces surfaces) {

    public record AuthenticationCapability(
            String sessionPath,
            boolean legacyPasswordAdapterEnabled) {}

    public record RuntimeSurfaces(
            RuntimeCapabilityState iamAdministration,
            RuntimeCapabilityState resourceAccessAdministration,
            RuntimeCapabilityState uiCapabilityProjection,
            RuntimeCapabilityState enforcementActivation,
            RuntimeCapabilityState a2aOperations,
            RuntimeCapabilityState issueTracking) {}
}
