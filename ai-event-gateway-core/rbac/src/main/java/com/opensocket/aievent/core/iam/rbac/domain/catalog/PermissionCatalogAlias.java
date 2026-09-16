package com.opensocket.aievent.core.iam.rbac.domain.catalog;

import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PermissionCatalogAlias(
        UUID revisionId,
        PermissionCode aliasCode,
        PermissionCode canonicalPermissionCode,
        PermissionAliasType aliasType,
        Instant validFrom,
        Optional<Instant> validUntil,
        String reason,
        Instant createdAt,
        String createdBy,
        long version) {

    public PermissionCatalogAlias {
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(aliasCode, "aliasCode");
        Objects.requireNonNull(canonicalPermissionCode, "canonicalPermissionCode");
        if (aliasCode.equals(canonicalPermissionCode)) {
            throw new IllegalArgumentException("alias cannot reference itself");
        }
        Objects.requireNonNull(aliasType, "aliasType");
        Objects.requireNonNull(validFrom, "validFrom");
        validUntil = validUntil == null ? Optional.empty() : validUntil;
        if (validUntil.isPresent() && !validUntil.get().isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        reason = required(reason, "reason", 500);
        Objects.requireNonNull(createdAt, "createdAt");
        createdBy = required(createdBy, "createdBy", 128);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String checked = value.trim();
        if (checked.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return checked;
    }
}
