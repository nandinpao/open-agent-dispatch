package com.opensocket.aievent.core.iam.rbac.domain.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PermissionCatalogPublication(
        UUID publicationId,
        UUID revisionId,
        Optional<UUID> previousRevisionId,
        String contentHash,
        int entryCount,
        int aliasCount,
        String actorId,
        String auditReason,
        String correlationId,
        Instant publishedAt) {
    public PermissionCatalogPublication {
        Objects.requireNonNull(publicationId, "publicationId");
        Objects.requireNonNull(revisionId, "revisionId");
        previousRevisionId = previousRevisionId == null ? Optional.empty() : previousRevisionId;
        contentHash = required(contentHash, "contentHash", 128);
        if (entryCount < 1 || aliasCount < 0) throw new IllegalArgumentException("invalid publication counts");
        actorId = required(actorId, "actorId", 128);
        auditReason = required(auditReason, "auditReason", 500);
        correlationId = correlationId == null ? "" : correlationId.trim();
        Objects.requireNonNull(publishedAt, "publishedAt");
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String checked = value.trim();
        if (checked.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return checked;
    }
}
