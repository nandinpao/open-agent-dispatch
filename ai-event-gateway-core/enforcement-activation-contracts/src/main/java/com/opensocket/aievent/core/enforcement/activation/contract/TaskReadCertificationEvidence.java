package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;
import java.util.UUID;

public record TaskReadCertificationEvidence(
        UUID certificationId,
        String tenantId,
        long authorityRevision,
        AuthorityMode routeMode,
        long sampleCount,
        TaskReadCertificationStatus deterministicOrderStatus,
        TaskReadCertificationStatus cursorPaginationStatus,
        TaskReadCertificationStatus nPlusOneStatus,
        TaskReadCertificationStatus forceRlsStatus,
        TaskReadCertificationStatus sensitiveFieldMaskingStatus,
        TaskReadCertificationStatus fallbackPauseStatus,
        TaskReadCertificationStatus loadTestStatus,
        TaskReadCertificationStatus overallStatus,
        String evidenceJson,
        String idempotencyKey,
        String requestHash,
        String certifiedBy,
        String correlationId,
        Instant certifiedAt) {
    public TaskReadCertificationEvidence {
        if (certificationId == null || tenantId == null || tenantId.isBlank() || routeMode == null
                || overallStatus == null || certifiedAt == null) {
            throw new IllegalArgumentException("Task read certification fields are required");
        }
        if (authorityRevision < 1 || sampleCount < 0) throw new IllegalArgumentException("Invalid certification counters");
        evidenceJson = evidenceJson == null || evidenceJson.isBlank() ? "{}" : evidenceJson.trim();
        if (idempotencyKey == null || idempotencyKey.isBlank() || requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("Task read certification idempotency fields are required");
        }
        idempotencyKey = idempotencyKey.trim();
        requestHash = requestHash.trim();
        certifiedBy = certifiedBy == null ? "unknown" : certifiedBy.trim();
        correlationId = correlationId == null ? "" : correlationId.trim();
    }
}
