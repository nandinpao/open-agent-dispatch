package com.opensocket.aievent.core.iam.rbac.domain.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PermissionCatalogDiff(
        UUID baseRevisionId,
        UUID targetRevisionId,
        List<PermissionCatalogDiffItem> items) {
    public PermissionCatalogDiff {
        Objects.requireNonNull(baseRevisionId, "baseRevisionId");
        Objects.requireNonNull(targetRevisionId, "targetRevisionId");
        items = items == null ? List.of() : List.copyOf(items);
    }
}
