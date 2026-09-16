package com.opensocket.aievent.core.enforcement.activation.core;

import java.time.Instant;
import java.util.List;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;

public record AuthoritySnapshotData(long revision, Instant publishedAt, String checksum, List<AuthorityRouteDefinition> routes) {
    public AuthoritySnapshotData {
        if (revision <= 0) throw new IllegalArgumentException("published revision must be positive");
        if (publishedAt == null) throw new IllegalArgumentException("publishedAt is required");
        checksum = checksum == null ? "" : checksum.trim();
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
