package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;
import java.util.UUID;

public record Wave0ReadPilotObservation(
        UUID observationId,
        Wave0ReadPilotEntryPoint entryPoint,
        String tenantId,
        long authorityRevision,
        AuthorityMode authorityMode,
        AuthorityPlane selectedPlane,
        Wave0ReadPilotSource servedBy,
        boolean shadowCompared,
        boolean fallbackUsed,
        Wave0ReadPilotMismatchCategory mismatchCategory,
        String legacyFingerprint,
        String targetFingerprint,
        long legacyDurationMicros,
        long targetDurationMicros,
        String legacyErrorCode,
        String targetErrorCode,
        String correlationId,
        Instant observedAt) {

    public Wave0ReadPilotObservation {
        if (observationId == null || entryPoint == null || authorityMode == null || selectedPlane == null || servedBy == null || mismatchCategory == null || observedAt == null) {
            throw new IllegalArgumentException("observation identity, authority, result and timestamp are required");
        }
        tenantId = normalize(tenantId, "INSTANCE");
        if (authorityRevision < 0 || legacyDurationMicros < 0 || targetDurationMicros < 0) throw new IllegalArgumentException("revision and durations must not be negative");
        legacyFingerprint = normalize(legacyFingerprint, "");
        targetFingerprint = normalize(targetFingerprint, "");
        legacyErrorCode = normalize(legacyErrorCode, "");
        targetErrorCode = normalize(targetErrorCode, "");
        correlationId = normalize(correlationId, observationId.toString());
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
