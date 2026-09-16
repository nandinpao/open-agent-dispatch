package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Objects;
import java.util.Set;

public record Permission(
        PermissionCode code,
        String resourceType,
        String actionCode,
        String description,
        RiskLevel riskLevel,
        Set<ScopeType> allowedScopes,
        PermissionStatus status,
        boolean systemManaged,
        long version) {
    public Permission {
        Objects.requireNonNull(code, "code");
        resourceType = required(resourceType, "resourceType", 96);
        actionCode = required(actionCode, "actionCode", 96);
        description = required(description, "description", 1000);
        Objects.requireNonNull(riskLevel, "riskLevel");
        allowedScopes = allowedScopes == null ? Set.of() : Set.copyOf(allowedScopes);
        if (allowedScopes.isEmpty()) throw new IllegalArgumentException("allowedScopes is required");
        Objects.requireNonNull(status, "status");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }
    public boolean active() { return status == PermissionStatus.ACTIVE; }
    public boolean highRisk() { return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL; }
    public boolean supports(ScopeType type) { return allowedScopes.contains(type); }
    public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String checked=value.trim(); if (checked.length()>max) throw new IllegalArgumentException(field+" exceeds "+max); return checked;
    }
}
