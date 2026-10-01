package com.opensocket.aievent.core.kernel.configuration.revision;

import java.time.OffsetDateTime;

/** Immutable revision identity plus controlled lifecycle metadata. */
public record RuntimeConfigurationRevision(
        String revisionId,
        String configSetId,
        long sequenceNo,
        String baseRevisionId,
        String rollbackOfRevisionId,
        String restoreSourceRevisionId,
        RuntimeConfigurationRevisionState state,
        int definitionSchemaVersion,
        String reason,
        String createdBy,
        OffsetDateTime createdAt,
        String validatedBy,
        OffsetDateTime validatedAt,
        String submittedBy,
        OffsetDateTime submittedAt,
        String approvedBy,
        OffsetDateTime approvedAt,
        String publishedBy,
        OffsetDateTime publishedAt,
        String updatedBy,
        OffsetDateTime updatedAt) {
}
