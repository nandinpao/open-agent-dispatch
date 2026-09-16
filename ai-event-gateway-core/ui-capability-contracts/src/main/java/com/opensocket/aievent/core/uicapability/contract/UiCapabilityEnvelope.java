package com.opensocket.aievent.core.uicapability.contract;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Short-lived contextual projection. Backend authorization remains the only security authority. */
public record UiCapabilityEnvelope(
        String contractVersion,
        String contextId,
        String tenantId,
        long principalEpoch,
        long catalogRevision,
        long policyVersion,
        String resourceRefHash,
        Long resourceVersion,
        String enforcementMode,
        List<UiCapability> capabilities,
        Instant expiresAt,
        Instant refreshAfter,
        String viewStateToken) {

    public UiCapabilityEnvelope {
        contractVersion = require(contractVersion, "contractVersion");
        if (!UiCapabilityContract.VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unsupported UI capability contract version: " + contractVersion);
        }
        contextId = require(contextId, "contextId");
        tenantId = require(tenantId, "tenantId");
        principalEpoch = UiWireNumbers.requireSafeNonNegative(principalEpoch, "principalEpoch");
        catalogRevision = UiWireNumbers.requireSafeNonNegative(catalogRevision, "catalogRevision");
        policyVersion = UiWireNumbers.requireSafeNonNegative(policyVersion, "policyVersion");
        resourceRefHash = normalize(resourceRefHash);
        resourceVersion = UiWireNumbers.requireSafeNonNegative(resourceVersion, "resourceVersion");
        enforcementMode = require(enforcementMode, "enforcementMode");
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        assertUniqueActions(capabilities);
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(refreshAfter, "refreshAfter");
        if (refreshAfter.isAfter(expiresAt)) {
            throw new IllegalArgumentException("refreshAfter must not be after expiresAt");
        }
        viewStateToken = normalize(viewStateToken);
    }

    private static void assertUniqueActions(List<UiCapability> capabilities) {
        Set<String> seen = new HashSet<>();
        for (UiCapability capability : capabilities) {
            Objects.requireNonNull(capability, "capability");
            if (!seen.add(capability.uiActionId())) {
                throw new IllegalArgumentException("duplicate uiActionId: " + capability.uiActionId());
            }
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
