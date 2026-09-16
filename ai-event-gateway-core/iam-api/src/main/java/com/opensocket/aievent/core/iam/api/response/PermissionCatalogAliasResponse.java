package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogAlias;
import java.time.Instant;
import java.util.UUID;

public record PermissionCatalogAliasResponse(UUID revisionId,String aliasCode,String canonicalPermissionCode,String aliasType,
        Instant validFrom,Instant validUntil,String reason,Instant createdAt,String createdBy,long version) {
    public static PermissionCatalogAliasResponse from(PermissionCatalogAlias value){return new PermissionCatalogAliasResponse(
            value.revisionId(),value.aliasCode().value(),value.canonicalPermissionCode().value(),value.aliasType().name(),
            value.validFrom(),value.validUntil().orElse(null),value.reason(),value.createdAt(),value.createdBy(),value.version());}
}
