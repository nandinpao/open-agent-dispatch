package com.opensocket.aievent.core.iam.rbac.domain.catalog;

import java.util.Objects;

public record PermissionCatalogValidationIssue(
        PermissionCatalogValidationSeverity severity,
        String code,
        String permissionCode,
        String message) {
    public PermissionCatalogValidationIssue {
        Objects.requireNonNull(severity, "severity");
        code = required(code, "code", 128);
        permissionCode = permissionCode == null ? "" : permissionCode.trim();
        message = required(message, "message", 1000);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String checked = value.trim();
        if (checked.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return checked;
    }
}
