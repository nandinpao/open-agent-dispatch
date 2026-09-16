package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionDefinition;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record PermissionDefinitionResponse(
        UUID revisionId,String permissionCode,String ownerModule,String resourceType,String actionCode,String description,
        String riskLevel,String riskLane,String lifecycle,Set<String> allowedScopes,boolean systemManaged,
        String replacementPermissionCode,Instant introducedAt,Instant deprecatedAt,Instant retiredAt,
        Instant updatedAt,String updatedBy,long version) {
    public static PermissionDefinitionResponse from(PermissionDefinition value){return new PermissionDefinitionResponse(
            value.catalogRevisionId(),value.code().value(),value.ownerModule(),value.resourceType(),value.actionCode(),value.description(),
            value.riskLevel(),value.riskLane().name(),value.lifecycle().name(),value.allowedScopes().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
            value.systemManaged(),value.replacementPermissionCode().map(code->code.value()).orElse(null),value.introducedAt(),
            value.deprecatedAt().orElse(null),value.retiredAt().orElse(null),value.updatedAt(),value.updatedBy(),value.version());}
}
