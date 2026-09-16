package com.opensocket.aievent.core.iam.rbac.domain.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PermissionCatalogValidationReport(
        UUID revisionId,
        boolean valid,
        int entryCount,
        int aliasCount,
        List<PermissionCatalogValidationIssue> issues) {
    public PermissionCatalogValidationReport {
        Objects.requireNonNull(revisionId, "revisionId");
        if (entryCount < 0 || aliasCount < 0) throw new IllegalArgumentException("counts cannot be negative");
        issues = issues == null ? List.of() : List.copyOf(issues);
        boolean hasError = issues.stream().anyMatch(issue ->
                issue.severity() == PermissionCatalogValidationSeverity.ERROR);
        if (valid == hasError) {
            throw new IllegalArgumentException("valid must be the inverse of error presence");
        }
    }
}
