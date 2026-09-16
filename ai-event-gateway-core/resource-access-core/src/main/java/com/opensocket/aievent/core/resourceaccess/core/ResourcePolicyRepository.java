package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.Optional;

/** Persistence port for P4RA-C policy governance. It does not evaluate business authorization. */
public interface ResourcePolicyRepository {
    Optional<ScopeGrantRecord> findScopeGrant(String tenantId,String grantId);
    Optional<ScopeGrantRecord> findScopeGrantByIdempotencyKey(String tenantId,String idempotencyKey);
    ScopeGrantRecord insertScopeGrant(ScopeGrantRecord grant,String correlationId);
    ScopeGrantRecord transitionScopeGrant(ScopeGrantRecord current,ScopeGrantState targetState,String approvedBy,
                                           String actorId,String reason,String correlationId,String idempotencyKey,Instant changedAt);

    Optional<ExplicitDenyRecord> findExplicitDeny(String tenantId,String denyId);
    Optional<ExplicitDenyRecord> findExplicitDenyByIdempotencyKey(String tenantId,String idempotencyKey);
    ExplicitDenyRecord insertExplicitDeny(ExplicitDenyRecord deny,String correlationId);
    ExplicitDenyRecord transitionExplicitDeny(ExplicitDenyRecord current,ScopeDenyState targetState,String approvedBy,
                                               String actorId,String reason,String correlationId,String idempotencyKey,Instant changedAt);

    Optional<VisibilityPolicyRecord> findVisibilityPolicy(String tenantId,String policyId);
    Optional<VisibilityPolicyRecord> findVisibilityPolicyByIdempotencyKey(String tenantId,String idempotencyKey);
    VisibilityPolicyRecord insertVisibilityPolicy(VisibilityPolicyRecord policy,String idempotencyKey,String correlationId);
    VisibilityPolicyRecord transitionVisibilityPolicy(VisibilityPolicyRecord current,VisibilityPolicyState targetState,
                                                        String actorId,String reason,String correlationId,String idempotencyKey,Instant changedAt);

    Optional<PrincipalClearanceRecord> findPrincipalClearance(String tenantId,String clearanceId);
    Optional<PrincipalClearanceRecord> findPrincipalClearanceByIdempotencyKey(String tenantId,String idempotencyKey);
    Optional<String> findPrincipalClearanceCreator(String tenantId,String clearanceId);
    PrincipalClearanceRecord insertPrincipalClearance(PrincipalClearanceRecord clearance,String createdBy,String idempotencyKey,String correlationId);
    PrincipalClearanceRecord transitionPrincipalClearance(PrincipalClearanceRecord current,ClearanceStatus targetStatus,String approvedBy,
                                                            String actorId,String reason,String correlationId,String idempotencyKey,Instant changedAt);

    Optional<SecurityStateChangeResult> findSecurityStateChange(ResourceRef resourceRef,String idempotencyKey);
    SecurityStateChangeResult changeSecurityState(SecurityStateChangeCommand command);
}
