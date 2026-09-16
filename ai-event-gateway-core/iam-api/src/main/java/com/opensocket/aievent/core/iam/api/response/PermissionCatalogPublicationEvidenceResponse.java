package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogPublication;
import java.time.Instant;
import java.util.UUID;

public record PermissionCatalogPublicationEvidenceResponse(
        UUID publicationId,
        UUID revisionId,
        UUID previousRevisionId,
        String contentHash,
        int entryCount,
        int aliasCount,
        String actorId,
        String auditReason,
        String correlationId,
        Instant publishedAt) {
    public static PermissionCatalogPublicationEvidenceResponse from(PermissionCatalogPublication value) {
        return new PermissionCatalogPublicationEvidenceResponse(
                value.publicationId(), value.revisionId(), value.previousRevisionId().orElse(null),
                value.contentHash(), value.entryCount(), value.aliasCount(), value.actorId(),
                value.auditReason(), value.correlationId(), value.publishedAt());
    }
}
