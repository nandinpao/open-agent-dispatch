package com.opensocket.aievent.core.uicapability.contract;

/** Server-route request. Page context is resolved by a server-owned catalog. */
public record UiPageBootstrapRequest(
        String contractVersion,
        String routeContext,
        String resourceId,
        Long resourceVersion,
        Long presentedPrincipalEpoch) {
    public UiPageBootstrapRequest {
        contractVersion = require(contractVersion, "contractVersion");
        routeContext = require(routeContext, "routeContext");
        resourceId = require(resourceId, "resourceId");
        resourceVersion = UiWireNumbers.requireSafeNonNegative(resourceVersion, "resourceVersion");
        presentedPrincipalEpoch = UiWireNumbers.requireSafeNonNegative(presentedPrincipalEpoch, "presentedPrincipalEpoch");
    }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
