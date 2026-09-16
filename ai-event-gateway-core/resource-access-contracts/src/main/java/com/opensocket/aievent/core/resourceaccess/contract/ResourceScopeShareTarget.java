package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Objects;

/**
 * Organizational target of an explicit Resource Scope Share.
 * A share expands resource discoverability to a scope but never grants an operation permission by itself.
 */
public record ResourceScopeShareTarget(
        String tenantId,
        CanonicalResourceScope scopeType,
        String scopeId) {

    public ResourceScopeShareTarget {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(scopeType, "scopeType");
        scopeId = required(scopeId, "scopeId");
        if (scopeType != CanonicalResourceScope.DEPARTMENT
                && scopeType != CanonicalResourceScope.DEPARTMENT_SUBTREE
                && scopeType != CanonicalResourceScope.GROUP) {
            throw new IllegalArgumentException("scope share target must be DEPARTMENT, DEPARTMENT_SUBTREE, or GROUP");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
