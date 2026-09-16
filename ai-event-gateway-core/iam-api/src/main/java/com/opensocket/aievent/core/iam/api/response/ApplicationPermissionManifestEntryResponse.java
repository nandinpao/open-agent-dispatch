package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.manifest.ApplicationPermissionManifestEntry;
import java.util.List;

public record ApplicationPermissionManifestEntryResponse(
        String manifestId,String entryPointId,String entryPointType,String ownerModule,String displayName,String routePattern,
        String httpMethod,String authorityState,String protectionMode,String coverageStatus,String permissionCode,
        List<String> legacyAuthorities,String resourceType,String resourceResolverId,boolean scopeRequired,String exemptionReason,
        String sourceRef,String sourceHash,String descriptorHash,String driftStatus,boolean blocker) {
    public static ApplicationPermissionManifestEntryResponse from(ApplicationPermissionManifestEntry v){
        return new ApplicationPermissionManifestEntryResponse(v.manifestId(),v.entryPointId(),v.entryPointType(),v.ownerModule(),v.displayName(),
                v.routePattern().orElse(null),v.httpMethod().orElse(null),v.authorityState(),v.protectionMode(),v.coverageStatus(),
                v.permissionCode().orElse(null),v.legacyAuthorities(),v.resourceType(),v.resourceResolverId(),v.scopeRequired(),
                v.exemptionReason().orElse(null),v.sourceRef(),v.sourceHash(),v.descriptorHash(),v.driftStatus(),v.blocker());
    }
}
