package com.opensocket.aievent.core.kernel.configuration.revision;

import java.time.OffsetDateTime;

/** Append-only governance evidence. Metadata is JSON text and must never contain raw secret material. */
public record RuntimeConfigurationAuditEntry(
        String auditId,
        String configSetId,
        String revisionId,
        String action,
        String actor,
        String reason,
        String correlationId,
        String metadataJson,
        OffsetDateTime createdAt) {
}
