package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.manifest.PermissionManifestDriftSummary;

public record PermissionManifestDriftSummaryResponse(
        String manifestId,long total,long matching,long sourceChanged,long descriptorChanged,long unregisteredSource,long staleManifest,
        long unknownPermission,long retiredPermission,long missingResolver,long blockers) {
    public static PermissionManifestDriftSummaryResponse from(PermissionManifestDriftSummary v){
        return new PermissionManifestDriftSummaryResponse(v.manifestId(),v.total(),v.matching(),v.sourceChanged(),v.descriptorChanged(),v.unregisteredSource(),
                v.staleManifest(),v.unknownPermission(),v.retiredPermission(),v.missingResolver(),v.blockers());
    }
}
