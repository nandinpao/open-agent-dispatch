package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
/** Immutable Phase 5I comparison evidence. It compares effect, scope, visibility, reason and context completeness. */
public record ShadowDecisionComparisonV2(
        String comparisonId,
        String tenantId,
        String domainCode,
        String entryPointId,
        ResourceRef resourceRef,
        String permissionCode,
        ShadowDecisionEvidenceV2 legacy,
        ShadowDecisionEvidenceV2 target,
        ShadowMismatchCategoryV2 category,
        String severity,
        String correlationId,
        Instant comparedAt) {
    public ShadowDecisionComparisonV2 {
        comparisonId=required(comparisonId,"comparisonId");tenantId=required(tenantId,"tenantId");domainCode=required(domainCode,"domainCode");entryPointId=entryPointId==null?"":entryPointId.trim();
        if(resourceRef==null)throw new IllegalArgumentException("resourceRef is required");permissionCode=required(permissionCode,"permissionCode");
        if(legacy==null||target==null||category==null)throw new IllegalArgumentException("comparison evidence is required");
        severity=required(severity,"severity");correlationId=required(correlationId,"correlationId");if(comparedAt==null)throw new IllegalArgumentException("comparedAt is required");
    }
    public boolean mismatch(){return category!=ShadowMismatchCategoryV2.MATCH;}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
}
