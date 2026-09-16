package com.opensocket.aievent.core.uicapability.contract;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Browser-safe request for one server-controlled resource context.
 * Tenant, permission code, resource type and visibility are intentionally absent.
 */
public record UiCapabilityContextRequest(
        String contextId,
        String resourceId,
        Long resourceVersion,
        Long presentedPrincipalEpoch,
        List<String> uiActionIds) {

    public UiCapabilityContextRequest {
        contextId = require(contextId, "contextId");
        resourceId = require(resourceId, "resourceId");
        resourceVersion = UiWireNumbers.requireSafeNonNegative(resourceVersion, "resourceVersion");
        presentedPrincipalEpoch = UiWireNumbers.requireSafeNonNegative(
                presentedPrincipalEpoch, "presentedPrincipalEpoch");
        uiActionIds = uiActionIds == null ? List.of() : List.copyOf(uiActionIds);
        if (uiActionIds.isEmpty()) throw new IllegalArgumentException("uiActionIds is required");
        Set<String> unique = new LinkedHashSet<>();
        for (String actionId : uiActionIds) {
            if (actionId == null || !actionId.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){2,}")) {
                throw new IllegalArgumentException("uiActionIds contains a non-canonical identifier");
            }
            if (!unique.add(actionId)) throw new IllegalArgumentException("duplicate uiActionId: " + actionId);
        }
        uiActionIds = List.copyOf(unique);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
