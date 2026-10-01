package com.opensocket.aievent.core.kernel.configuration.governance;

import java.time.OffsetDateTime;

/** V40-7 immutable key-level emergency overlay metadata. */
public record RuntimeConfigurationEmergencyOverride(
        String overrideId, String configSetId, String definitionKey, String baseRevisionId,
        String valueJson, String valueFingerprint, String status, String reason,
        String createdBy, OffsetDateTime createdAt, OffsetDateTime expiresAt,
        String revokedBy, OffsetDateTime revokedAt, String revokeReason) {
    public boolean activeAt(OffsetDateTime now) {
        return "ACTIVE".equals(status) && expiresAt != null && expiresAt.isAfter(now);
    }
}
