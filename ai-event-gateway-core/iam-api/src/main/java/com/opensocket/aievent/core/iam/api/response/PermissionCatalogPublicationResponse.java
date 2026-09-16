package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogPublication;
import java.time.Instant;
import java.util.UUID;

public record PermissionCatalogPublicationResponse(UUID publicationId,UUID revisionId,UUID previousRevisionId,
        String contentHash,int entryCount,int aliasCount,String actorId,Instant publishedAt) {
    public static PermissionCatalogPublicationResponse from(PermissionCatalogPublication value){return new PermissionCatalogPublicationResponse(
            value.publicationId(),value.revisionId(),value.previousRevisionId().orElse(null),value.contentHash(),value.entryCount(),
            value.aliasCount(),value.actorId(),value.publishedAt());}
}
