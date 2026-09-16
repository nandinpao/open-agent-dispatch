package com.opensocket.aievent.core.resourceaccess.core;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;import java.util.*;
/** Read/write port for authorization evidence and immutable decision audit. */
public interface ResourceDecisionEvidenceRepository {
 List<ScopeGrantRecord> findEffectiveScopeGrants(String tenantId,Set<PolicyPrincipalRef> principals,String permissionCode,ResourceType resourceType,Instant at);
 List<ExplicitDenyRecord> findEffectiveExplicitDenies(String tenantId,Set<PolicyPrincipalRef> principals,String permissionCode,ResourceType resourceType,Instant at);
 List<ResourceParticipantProjection> findEffectiveParticipants(ResourceRef resourceRef,Instant at);
 Optional<PrincipalClearanceRecord> findEffectiveClearance(String tenantId,PolicyPrincipalRef principal,Instant at);
 Optional<VisibilityPolicyRecord> findActiveVisibilityPolicy(String tenantId,ResourceType resourceType);
 PrincipalScopeSnapshot resolvePrincipalScope(String tenantId,PrincipalRef principal,Instant at);
 boolean departmentContains(String tenantId,String ancestorDepartmentId,String descendantDepartmentId);
 PolicyVersion currentPolicyVersion(String tenantId);
 SecurityEpoch currentSecurityEpoch(String tenantId,PrincipalRef principal,ResourceRef resourceRef);
 void appendDecision(AuthorizationDecisionAuditRecord record);
 void appendShadowComparison(ShadowDecisionComparison comparison);
 default void appendShadowComparisonV2(ShadowDecisionComparisonV2 comparison) { }
}
