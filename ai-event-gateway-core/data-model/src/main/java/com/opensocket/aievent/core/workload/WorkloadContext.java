package com.opensocket.aievent.core.workload;

import java.time.Instant;

/**
 * Immutable creation-time workload provenance. This is historical evidence, not a live
 * authorization object. Current RBAC/resource authorization must never be inferred from it.
 */
public record WorkloadContext(
        String tenantId,
        String originPrincipalType,
        String originPrincipalId,
        String actorPrincipalType,
        String actorPrincipalId,
        String credentialId,
        String oauthClientId,
        String departmentId,
        String groupId,
        String organizationSource,
        String organizationStatus,
        String sourceSystem,
        String apiResource,
        String clientIp,
        String authorizationDecisionId,
        long globalSecurityEpoch,
        long tenantSecurityEpoch,
        long principalSecurityEpoch,
        String requestId,
        String correlationId,
        String traceId,
        String authenticationMethod,
        String workloadPurpose,
        boolean synthetic,
        Instant capturedAt) {

    public WorkloadContext {
        tenantId = required(tenantId, "tenantId", 128);
        originPrincipalType = required(originPrincipalType, "originPrincipalType", 32);
        originPrincipalId = required(originPrincipalId, "originPrincipalId", 128);
        actorPrincipalType = required(actorPrincipalType, "actorPrincipalType", 32);
        actorPrincipalId = required(actorPrincipalId, "actorPrincipalId", 128);
        credentialId = optional(credentialId, 128);
        oauthClientId = optional(oauthClientId, 96);
        departmentId = required(defaultValue(departmentId, "UNASSIGNED"), "departmentId", 128);
        groupId = optional(groupId, 128);
        organizationSource = required(defaultValue(organizationSource, "UNRESOLVED"), "organizationSource", 32);
        organizationStatus = required(defaultValue(organizationStatus, "UNRESOLVED"), "organizationStatus", 32);
        sourceSystem = optional(sourceSystem, 160);
        apiResource = optional(apiResource, 256);
        clientIp = optional(clientIp, 128);
        authorizationDecisionId = optional(authorizationDecisionId, 128);
        if (globalSecurityEpoch < 0 || tenantSecurityEpoch < 0 || principalSecurityEpoch < 0) {
            throw new IllegalArgumentException("security epochs must be non-negative");
        }
        requestId = optional(requestId, 128);
        correlationId = optional(correlationId, 128);
        traceId = optional(traceId, 64);
        authenticationMethod = required(defaultValue(authenticationMethod, "UNKNOWN"), "authenticationMethod", 64);
        workloadPurpose = required(defaultValue(workloadPurpose, "PRODUCTION"), "workloadPurpose", 32).toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("PRODUCTION","TEST","CERTIFICATION","SIMULATION").contains(workloadPurpose)) {
            throw new IllegalArgumentException("unsupported workloadPurpose: " + workloadPurpose);
        }
        if (capturedAt == null) capturedAt = Instant.EPOCH;
    }

    public static WorkloadContext unattributed(String tenantId, String sourceSystem, String correlationId, Instant capturedAt) {
        return new WorkloadContext(defaultValue(tenantId, "UNKNOWN"), "SYSTEM", "CORE", "SYSTEM", "CORE",
                "", "", "UNASSIGNED", "", "UNRESOLVED", "UNRESOLVED", sourceSystem, "", "", "",
                0, 0, 0, "", correlationId, "", "UNATTRIBUTED", "PRODUCTION", false, capturedAt == null ? Instant.EPOCH : capturedAt);
    }

    public boolean organizationResolved() { return "RESOLVED".equalsIgnoreCase(organizationStatus); }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String v=value.trim(); if(v.length()>max) throw new IllegalArgumentException(field + " exceeds " + max + " characters"); return v;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return "";
        String v=value.trim(); if(v.length()>max) throw new IllegalArgumentException("value exceeds " + max + " characters"); return v;
    }
    private static String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
