package com.opensocket.aievent.core.identity;

import java.util.Set;

/** Authoritative account record loaded by the Core identity repository. */
public record AdminAccount(
        String userId,
        String username,
        String displayName,
        String passwordHash,
        Set<AdminRole> roles,
        Set<String> allowedTenantIds,
        String defaultTenantId,
        boolean enabled
) {
    public AdminAccount {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        allowedTenantIds = allowedTenantIds == null ? Set.of() : Set.copyOf(allowedTenantIds);
    }
}
