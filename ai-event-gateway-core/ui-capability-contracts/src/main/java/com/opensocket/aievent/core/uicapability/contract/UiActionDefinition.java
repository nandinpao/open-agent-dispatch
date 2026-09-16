package com.opensocket.aievent.core.uicapability.contract;

import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;

/** Build-time action mapping metadata. This metadata is not sent as a complete catalog to browsers. */
public record UiActionDefinition(
        String uiActionId,
        String domain,
        String resourceType,
        String canonicalPermissionCode,
        VisibilityLevel requestedVisibility,
        String entryPointProfile,
        String riskLane,
        String stepUpProfile,
        String approvalProfile,
        String requestAccessProfile,
        String lifecycle) {

    public UiActionDefinition {
        uiActionId = require(uiActionId, "uiActionId");
        domain = require(domain, "domain");
        resourceType = require(resourceType, "resourceType");
        canonicalPermissionCode = require(canonicalPermissionCode, "canonicalPermissionCode");
        requestedVisibility = requestedVisibility == null ? VisibilityLevel.NONE : requestedVisibility;
        entryPointProfile = require(entryPointProfile, "entryPointProfile");
        riskLane = require(riskLane, "riskLane");
        stepUpProfile = normalize(stepUpProfile);
        approvalProfile = normalize(approvalProfile);
        requestAccessProfile = normalize(requestAccessProfile);
        lifecycle = require(lifecycle, "lifecycle");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
