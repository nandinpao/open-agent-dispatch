package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogValidationReport;
import java.util.List;
import java.util.UUID;

public record PermissionCatalogValidationResponse(UUID revisionId,boolean valid,int entryCount,int aliasCount,List<Issue> issues) {
    public record Issue(String severity,String code,String permissionCode,String message) {}
    public static PermissionCatalogValidationResponse from(PermissionCatalogValidationReport value){return new PermissionCatalogValidationResponse(
            value.revisionId(),value.valid(),value.entryCount(),value.aliasCount(),value.issues().stream()
            .map(issue->new Issue(issue.severity().name(),issue.code(),issue.permissionCode(),issue.message())).toList());}
}
