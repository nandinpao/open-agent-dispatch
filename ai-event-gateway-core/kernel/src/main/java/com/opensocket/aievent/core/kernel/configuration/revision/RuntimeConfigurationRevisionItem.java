package com.opensocket.aievent.core.kernel.configuration.revision;

import java.time.OffsetDateTime;

/** Canonical JSON value owned by one draft/revision. */
public record RuntimeConfigurationRevisionItem(
        String revisionId,
        String definitionKey,
        String valueJson,
        String valueFingerprint,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt) {
}
