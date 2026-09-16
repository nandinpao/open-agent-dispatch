package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant; import java.util.Set;
/** Append-only evidence for Integration scope plans and SHADOW list differences. */
public interface IntegrationScopeQueryAuditPort {
 void recordPlan(IntegrationScopeQueryPlan plan,String purpose,Instant evaluatedAt);
 void recordShadowMismatch(IntegrationScopeQueryPlan plan,String purpose,Set<String> legacyOnly,Set<String> scopedOnly,Instant observedAt);
}
