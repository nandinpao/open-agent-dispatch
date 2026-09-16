package com.opensocket.aievent.core.enforcement.activation.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteKey;

public record AuthoritySnapshot(
        long revision,
        Instant publishedAt,
        String checksum,
        Map<AuthorityRouteKey, AuthorityRouteDefinition> exactRoutes,
        List<AuthorityRouteDefinition> wildcardRoutes) {

    public AuthoritySnapshot {
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        if (publishedAt == null) throw new IllegalArgumentException("publishedAt is required");
        checksum = checksum == null ? "" : checksum;
        exactRoutes = Map.copyOf(exactRoutes);
        wildcardRoutes = List.copyOf(wildcardRoutes);
    }

    public static AuthoritySnapshot bootstrap() {
        return new AuthoritySnapshot(0, Instant.EPOCH, "BOOTSTRAP_LEGACY_ONLY", Map.of(), List.of());
    }
}
