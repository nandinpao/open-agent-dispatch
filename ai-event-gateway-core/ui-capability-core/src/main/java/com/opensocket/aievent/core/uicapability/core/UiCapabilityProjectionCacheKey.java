package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.uicapability.contract.UiWireNumbers;

/** Exact authority namespace key. The action profile digest prevents cross-action envelope reuse. */
public record UiCapabilityProjectionCacheKey(
        String tenantId,
        String principalId,
        String contextId,
        String resourceRefHash,
        long resourceVersion,
        long catalogRevision,
        long policyVersion,
        long principalEpoch,
        long resourceEpoch,
        String actionProfileDigest) {
    public UiCapabilityProjectionCacheKey {
        tenantId = require(tenantId, "tenantId");
        principalId = require(principalId, "principalId");
        contextId = require(contextId, "contextId");
        resourceRefHash = require(resourceRefHash, "resourceRefHash");
        resourceVersion = UiWireNumbers.requireSafeNonNegative(resourceVersion, "resourceVersion");
        catalogRevision = UiWireNumbers.requireSafeNonNegative(catalogRevision, "catalogRevision");
        policyVersion = UiWireNumbers.requireSafeNonNegative(policyVersion, "policyVersion");
        principalEpoch = UiWireNumbers.requireSafeNonNegative(principalEpoch, "principalEpoch");
        resourceEpoch = UiWireNumbers.requireSafeNonNegative(resourceEpoch, "resourceEpoch");
        actionProfileDigest = require(actionProfileDigest, "actionProfileDigest");
    }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
