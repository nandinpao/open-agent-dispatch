package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReadinessEvidenceBinding(
        UUID evidenceId,
        ReadinessEvidenceType evidenceType,
        String tenantId,
        String domainCode,
        String status,
        String checksum,
        Instant evaluatedAt,
        Instant expiresAt,
        String sourceRevision,
        String immutablePayload) {

    public ReadinessEvidenceBinding {
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(evidenceType, "evidenceType");
        tenantId = required(tenantId, "tenantId", 64);
        domainCode = normalized(domainCode, 64);
        status = required(status, "status", 32).toUpperCase();
        checksum = required(checksum, "checksum", 71);
        if (!checksum.startsWith("sha256:")) throw new IllegalArgumentException("checksum must use sha256");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        sourceRevision = normalized(sourceRevision, 128);
        immutablePayload = immutablePayload == null || immutablePayload.isBlank() ? "{}" : immutablePayload.trim();
    }

    public boolean eligibleAt(Instant instant) {
        boolean statusAllowed = "ELIGIBLE".equals(status) || "PASS".equals(status) || "PASSED".equals(status) || "CERTIFIED".equals(status);
        return statusAllowed && (expiresAt == null || expiresAt.isAfter(instant));
    }

    private static String required(String value, String field, int max) {
        String result = normalized(value, max);
        if (result.isBlank()) throw new IllegalArgumentException(field + " is required");
        return result;
    }

    private static String normalized(String value, int max) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max) throw new IllegalArgumentException("value exceeds " + max);
        return result;
    }
}
