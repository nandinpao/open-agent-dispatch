package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogDiff;
import java.util.List;
import java.util.UUID;

public record PermissionCatalogDiffResponse(UUID baseRevisionId,UUID targetRevisionId,List<Item> items) {
    public record Item(String permissionCode,String type,List<String> changedFields,
                       PermissionDefinitionResponse before,PermissionDefinitionResponse after) {}
    public static PermissionCatalogDiffResponse from(PermissionCatalogDiff value){return new PermissionCatalogDiffResponse(
            value.baseRevisionId(),value.targetRevisionId(),value.items().stream().map(item->new Item(
            item.permissionCode().value(),item.type().name(),item.changedFields(),item.before().map(PermissionDefinitionResponse::from).orElse(null),
            item.after().map(PermissionDefinitionResponse::from).orElse(null))).toList());}
}
