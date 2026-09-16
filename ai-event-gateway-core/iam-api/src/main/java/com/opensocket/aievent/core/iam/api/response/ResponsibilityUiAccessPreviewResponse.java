package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.Set;

/** Business-facing preview of the Navigator, pages and actions produced by one Responsibility. */
public record ResponsibilityUiAccessPreviewResponse(
        String tenantId,
        String roleId,
        Instant generatedAt,
        Set<String> permissionCodes,
        Set<String> allowedScopeTypes,
        UiEntitlementResponse uiAccess) {
    public ResponsibilityUiAccessPreviewResponse {
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        allowedScopeTypes = allowedScopeTypes == null ? Set.of() : Set.copyOf(allowedScopeTypes);
    }
}
