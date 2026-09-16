package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;
import java.util.UUID;

public record Wave0ReadinessEvidenceView(
        UUID evidenceId,
        ReadinessEvidenceType evidenceType,
        String tenantId,
        String domainCode,
        String status,
        String checksum,
        Instant evaluatedAt,
        Instant expiresAt,
        String sourceRevision) implements Wave0CanonicalPayload {

    public Wave0ReadinessEvidenceView {
        if (evidenceId == null || evidenceType == null || evaluatedAt == null) throw new IllegalArgumentException("evidence identity and timestamp are required");
        tenantId = normalize(tenantId);
        domainCode = normalize(domainCode);
        status = normalize(status);
        checksum = normalize(checksum);
        sourceRevision = normalize(sourceRevision);
    }

    @Override public String canonicalValue() {
        return evidenceId + "|" + evidenceType + "|" + tenantId + "|" + domainCode + "|" + status + "|" + checksum + "|" + evaluatedAt + "|" + expiresAt + "|" + sourceRevision;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
