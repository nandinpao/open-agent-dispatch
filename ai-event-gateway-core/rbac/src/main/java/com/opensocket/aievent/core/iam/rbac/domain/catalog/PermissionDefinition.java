package com.opensocket.aievent.core.iam.rbac.domain.catalog;

import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** PostgreSQL permission_definitions row used by Catalog administration. */
public record PermissionDefinition(
        PermissionCode code,
        String ownerModule,
        String resourceType,
        String actionCode,
        String description,
        String riskLevel,
        PermissionRiskLane riskLane,
        PermissionLifecycle lifecycle,
        Set<ScopeType> allowedScopes,
        boolean systemManaged,
        UUID catalogRevisionId,
        Optional<PermissionCode> replacementPermissionCode,
        Instant introducedAt,
        Optional<Instant> deprecatedAt,
        Optional<Instant> retiredAt,
        Instant updatedAt,
        String updatedBy,
        long version) {

    public PermissionDefinition {
        Objects.requireNonNull(code, "code");
        ownerModule = required(ownerModule, "ownerModule", 128);
        resourceType = required(resourceType, "resourceType", 96);
        actionCode = required(actionCode, "actionCode", 96);
        description = required(description, "description", 1000);
        riskLevel = required(riskLevel, "riskLevel", 24);
        Objects.requireNonNull(riskLane, "riskLane");
        Objects.requireNonNull(lifecycle, "lifecycle");
        allowedScopes = allowedScopes == null ? Set.of() : Set.copyOf(allowedScopes);
        if (allowedScopes.isEmpty()) throw new IllegalArgumentException("allowedScopes is required");
        Objects.requireNonNull(catalogRevisionId, "catalogRevisionId");
        replacementPermissionCode = replacementPermissionCode == null
                ? Optional.empty() : replacementPermissionCode;
        replacementPermissionCode.ifPresent(replacement -> {
            if (replacement.equals(code)) {
                throw new IllegalArgumentException("replacement permission cannot reference itself");
            }
        });
        Objects.requireNonNull(introducedAt, "introducedAt");
        deprecatedAt = deprecatedAt == null ? Optional.empty() : deprecatedAt;
        retiredAt = retiredAt == null ? Optional.empty() : retiredAt;
        if (lifecycle == PermissionLifecycle.DEPRECATED && deprecatedAt.isEmpty()) {
            throw new IllegalArgumentException("deprecated permission requires deprecatedAt");
        }
        if (lifecycle == PermissionLifecycle.RETIRED && retiredAt.isEmpty()) {
            throw new IllegalArgumentException("retired permission requires retiredAt");
        }
        Objects.requireNonNull(updatedAt, "updatedAt");
        updatedBy = required(updatedBy, "updatedBy", 128);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    public boolean runtimeActive() {
        return lifecycle == PermissionLifecycle.ACTIVE || lifecycle == PermissionLifecycle.DEPRECATED;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String checked = value.trim();
        if (checked.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return checked;
    }
}
