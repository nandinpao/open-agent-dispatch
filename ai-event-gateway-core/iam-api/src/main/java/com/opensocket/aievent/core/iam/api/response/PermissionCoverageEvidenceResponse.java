package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.manifest.PermissionCoverageEvidence;
import java.math.BigDecimal;
import java.time.Instant;

public record PermissionCoverageEvidenceResponse(
        String evidenceId,String manifestId,String evidenceType,String manifestHash,String catalogRevisionId,
        String catalogContentHash,int entryCount,int coveredEntryCount,BigDecimal coveragePercent,String sourceInventoryRevision,
        String detailsJson,String actorId,String correlationId,Instant occurredAt) {
    public static PermissionCoverageEvidenceResponse from(PermissionCoverageEvidence v){
        return new PermissionCoverageEvidenceResponse(v.evidenceId(),v.manifestId(),v.evidenceType(),v.manifestHash(),v.catalogRevisionId(),
                v.catalogContentHash(),v.entryCount(),v.coveredEntryCount(),v.coveragePercent(),v.sourceInventoryRevision(),
                v.detailsJson(),v.actorId(),v.correlationId().orElse(null),v.occurredAt());
    }
}
