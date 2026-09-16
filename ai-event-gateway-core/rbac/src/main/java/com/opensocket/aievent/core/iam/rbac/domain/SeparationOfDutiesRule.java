package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Objects;

public record SeparationOfDutiesRule(
        String ruleId,
        String tenantId,
        RoleId leftRoleId,
        RoleId rightRoleId,
        boolean scopeOverlapRequired,
        String description) {
    public SeparationOfDutiesRule {
        ruleId = required(ruleId, "ruleId");
        tenantId = tenantId == null ? "" : tenantId.trim();
        Objects.requireNonNull(leftRoleId, "leftRoleId");
        Objects.requireNonNull(rightRoleId, "rightRoleId");
        description = description == null ? "" : description.trim();
    }

    public boolean conflicts(RoleId first, RoleId second) {
        return (leftRoleId.equals(first) && rightRoleId.equals(second))
                || (leftRoleId.equals(second) && rightRoleId.equals(first));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
