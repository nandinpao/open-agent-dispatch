package com.opensocket.aievent.core.organization;

/**
 * Tenant-aware ownership references attached to an Agent Profile or Task.
 * Group is optional; Department and Service Domain use explicit UNASSIGNED
 * migration placeholders until an administrator assigns real ownership.
 */
public record OrganizationScope(
        String tenantId,
        String departmentId,
        String groupId,
        String serviceDomainId,
        String trustZoneId
) {
    public OrganizationScope {
        tenantId = require(tenantId, "tenantId");
        departmentId = defaultValue(departmentId, "UNASSIGNED");
        groupId = optional(groupId);
        serviceDomainId = defaultValue(serviceDomainId, "UNASSIGNED");
        trustZoneId = defaultValue(trustZoneId, "DEFAULT");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
