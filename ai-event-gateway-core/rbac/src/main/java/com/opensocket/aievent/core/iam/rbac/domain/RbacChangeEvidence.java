package com.opensocket.aievent.core.iam.rbac.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RbacChangeEvidence(
        String evidenceId,
        String tenantId,
        String operation,
        String actorId,
        String targetType,
        String targetId,
        String approvalId,
        String beforeJson,
        String afterJson,
        List<String> addedPermissions,
        List<String> removedPermissions,
        List<String> warnings,
        String correlationId,
        String auditReason,
        Instant occurredAt) {
    public RbacChangeEvidence {
        evidenceId = required(evidenceId, "evidenceId");
        tenantId = authorityScope(tenantId);
        operation = required(operation, "operation");
        actorId = required(actorId, "actorId");
        targetType = required(targetType, "targetType");
        targetId = required(targetId, "targetId");
        approvalId = approvalId == null ? "" : approvalId.trim();
        beforeJson = beforeJson == null || beforeJson.isBlank() ? "{}" : beforeJson;
        afterJson = afterJson == null || afterJson.isBlank() ? "{}" : afterJson;
        addedPermissions = addedPermissions == null ? List.of() : List.copyOf(addedPermissions);
        removedPermissions = removedPermissions == null ? List.of() : List.copyOf(removedPermissions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        correlationId = correlationId == null ? "" : correlationId.trim();
        auditReason = required(auditReason, "auditReason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
    private static String authorityScope(String value) {
        return value == null || value.isBlank() ? "INSTANCE" : value.trim();
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
