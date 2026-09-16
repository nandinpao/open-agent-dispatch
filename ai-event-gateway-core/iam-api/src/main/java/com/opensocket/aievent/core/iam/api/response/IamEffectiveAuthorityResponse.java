package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Canonical human-user authorization projection for one Tenant.
 * Session, UI entitlement and Effective Access consumers must derive from this projection
 * instead of re-querying direct USER Role Bindings independently.
 */
public record IamEffectiveAuthorityResponse(
        String tenantId,
        String userId,
        Instant evaluatedAt,
        Set<String> roleCodes,
        Set<String> permissionCodes,
        Map<String, Set<String>> permissionScopes,
        EffectiveAccessResponse effectiveAccess) {
    public IamEffectiveAuthorityResponse {
        roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        permissionScopes = permissionScopes == null ? Map.of() : permissionScopes.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
