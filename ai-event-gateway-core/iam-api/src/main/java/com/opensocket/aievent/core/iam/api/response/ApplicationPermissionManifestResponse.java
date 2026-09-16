package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.manifest.ApplicationPermissionManifest;
import java.math.BigDecimal;
import java.time.Instant;

public record ApplicationPermissionManifestResponse(
        String manifestId,String applicationId,String environment,String buildVersion,String manifestRevision,
        int schemaVersion,String manifestHash,String catalogRevisionId,String catalogRevisionCode,String catalogContentHash,
        String sourceInventoryRevision,int entryCount,int protectedEntryCount,int coveredEntryCount,BigDecimal coveragePercent,
        int targetPermissionCount,int legacyAuthorityCount,int exemptCount,int delegatedCount,int uncoveredCount,String status,
        Instant registeredAt,String registeredBy,Instant activatedAt,String activatedBy,long version,long matchingEntries,
        long sourceChanged,long descriptorChanged,long unregisteredSource,long staleManifest,long unknownPermission,long retiredPermission,
        long missingResolver,long runtimeDriftBlockers) {
    public static ApplicationPermissionManifestResponse from(ApplicationPermissionManifest v){
        return new ApplicationPermissionManifestResponse(v.manifestId(),v.applicationId(),v.environment(),v.buildVersion(),v.manifestRevision(),
                v.schemaVersion(),v.manifestHash(),v.catalogRevisionId(),v.catalogRevisionCode(),v.catalogContentHash(),v.sourceInventoryRevision(),
                v.entryCount(),v.protectedEntryCount(),v.coveredEntryCount(),v.coveragePercent(),v.targetPermissionCount(),v.legacyAuthorityCount(),
                v.exemptCount(),v.delegatedCount(),v.uncoveredCount(),v.status(),v.registeredAt(),v.registeredBy(),v.activatedAt().orElse(null),
                v.activatedBy().orElse(null),v.version(),v.matchingEntries(),v.sourceChanged(),v.descriptorChanged(),v.unregisteredSource(),v.staleManifest(),
                v.unknownPermission(),v.retiredPermission(),v.missingResolver(),v.runtimeDriftBlockers());
    }
}
