package com.opensocket.aievent.core.iam.rbac.domain.catalog;

import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PermissionCatalogDiffItem(
        PermissionCode permissionCode,
        PermissionCatalogDiffType type,
        Optional<PermissionDefinition> before,
        Optional<PermissionDefinition> after,
        List<String> changedFields) {

    public PermissionCatalogDiffItem {
        Objects.requireNonNull(permissionCode, "permissionCode");
        Objects.requireNonNull(type, "type");
        before = before == null ? Optional.empty() : before;
        after = after == null ? Optional.empty() : after;
        changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }
}
