package com.opensocket.aievent.core.iam.rbac.domain.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable catalog publication evidence. Runtime authorization reads permission_definitions. */
public record PermissionCatalogRevision(
        UUID revisionId,
        String revisionCode,
        long revisionNumber,
        PermissionCatalogRevisionStatus status,
        String contentHash,
        String description,
        Optional<UUID> supersedesRevisionId,
        Instant createdAt,
        String createdBy,
        Optional<Instant> publishedAt,
        Optional<String> publishedBy,
        long version) {

    public PermissionCatalogRevision {
        Objects.requireNonNull(revisionId, "revisionId");
        revisionCode = required(revisionCode, "revisionCode", 128);
        if (revisionNumber < 1) throw new IllegalArgumentException("revisionNumber must be positive");
        Objects.requireNonNull(status, "status");
        contentHash = required(contentHash, "contentHash", 128);
        description = description == null ? "" : description.trim();
        supersedesRevisionId = supersedesRevisionId == null ? Optional.empty() : supersedesRevisionId;
        Objects.requireNonNull(createdAt, "createdAt");
        createdBy = required(createdBy, "createdBy", 128);
        publishedAt = publishedAt == null ? Optional.empty() : publishedAt;
        publishedBy = publishedBy == null ? Optional.empty() : publishedBy.map(String::trim);
        if (status == PermissionCatalogRevisionStatus.DRAFT
                && (publishedAt.isPresent() || publishedBy.isPresent())) {
            throw new IllegalArgumentException("draft revision cannot be published");
        }
        if (status != PermissionCatalogRevisionStatus.DRAFT
                && (publishedAt.isEmpty() || publishedBy.isEmpty())) {
            throw new IllegalArgumentException("published revision requires publication evidence");
        }
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String checked = value.trim();
        if (checked.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return checked;
    }
}
