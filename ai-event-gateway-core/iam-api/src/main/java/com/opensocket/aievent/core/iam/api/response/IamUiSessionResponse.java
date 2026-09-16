package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record IamUiSessionResponse(
        String authenticationType,
        String userId,
        String username,
        String displayName,
        Set<String> roles,
        Set<String> permissions,
        Map<String, Set<String>> permissionScopes,
        String selectedTenantId,
        List<TenantChoiceResponse> tenantChoices,
        Set<String> requiredActions,
        long credentialVersion,
        Instant authenticatedAt,
        Instant expiresAt,
        Set<String> authenticationMethods) {
    public IamUiSessionResponse {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        permissionScopes = permissionScopes == null ? Map.of() : permissionScopes.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        tenantChoices = tenantChoices == null ? List.of() : List.copyOf(tenantChoices);
        requiredActions = requiredActions == null ? Set.of() : Set.copyOf(requiredActions);
        authenticationMethods = authenticationMethods == null ? Set.of() : Set.copyOf(authenticationMethods);
    }
}
