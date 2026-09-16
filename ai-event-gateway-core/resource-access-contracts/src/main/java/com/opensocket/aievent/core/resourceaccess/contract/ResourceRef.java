package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Objects;

/** Untrusted callers may identify a resource only through this opaque, tenant-bound reference. */
public record ResourceRef(String tenantId, ResourceType resourceType, String resourceId) {
    public ResourceRef {
        tenantId = requireText(tenantId, "tenantId");
        Objects.requireNonNull(resourceType, "resourceType");
        resourceId = requireText(resourceId, "resourceId");
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
