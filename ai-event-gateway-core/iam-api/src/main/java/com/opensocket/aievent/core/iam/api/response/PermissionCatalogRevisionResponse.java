package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogRevision;
import java.time.Instant;
import java.util.UUID;

public record PermissionCatalogRevisionResponse(
        UUID revisionId,String revisionCode,long revisionNumber,String status,String contentHash,String description,
        UUID supersedesRevisionId,Instant createdAt,String createdBy,Instant publishedAt,String publishedBy,long version) {
    public static PermissionCatalogRevisionResponse from(PermissionCatalogRevision value){return new PermissionCatalogRevisionResponse(
            value.revisionId(),value.revisionCode(),value.revisionNumber(),value.status().name(),value.contentHash(),value.description(),
            value.supersedesRevisionId().orElse(null),value.createdAt(),value.createdBy(),value.publishedAt().orElse(null),
            value.publishedBy().orElse(null),value.version());}
}
