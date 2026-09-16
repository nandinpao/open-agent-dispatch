package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
/** Immutable legacy/new comparison evidence. */
public record ShadowDecisionComparison(String comparisonId,String tenantId,ResourceRef resourceRef,String permissionCode,LegacyAuthorizationDecision legacyDecision,AuthorizationDecision resourceDecision,ShadowMismatchCategory category,String correlationId,Instant comparedAt){
 public ShadowDecisionComparison{comparisonId=req(comparisonId,"comparisonId");tenantId=req(tenantId,"tenantId");if(resourceRef==null)throw new IllegalArgumentException("resourceRef is required");permissionCode=req(permissionCode,"permissionCode");if(legacyDecision==null)throw new IllegalArgumentException("legacyDecision is required");if(resourceDecision==null)throw new IllegalArgumentException("resourceDecision is required");if(category==null)throw new IllegalArgumentException("category is required");correlationId=req(correlationId,"correlationId");if(comparedAt==null)throw new IllegalArgumentException("comparedAt is required");}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
