package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Formal Resource Access decision; distinct from IAM RBAC and legacy API admission evidence. */
public record AuthorizationDecision(
        String decisionId,
        DecisionEffect effect,
        AuthorizationDecisionMode mode,
        String permissionCode,
        ResourceRef resourceRef,
        VisibilityLevel grantedVisibility,
        Set<String> matchedRoleBindingIds,
        Set<String> matchedScopeGrantIds,
        Set<String> matchedParticipantIds,
        Set<String> matchedOwnershipEvidence,
        Set<String> matchedDenyIds,
        Set<String> matchedScopeSources,
        PolicyVersion policyVersion,
        SecurityEpoch securityEpoch,
        String descriptorHash,
        String runtimeLeaseId,
        List<DecisionReason> reasons,
        Instant evaluatedAt,
        boolean shadowOnly,
        boolean cacheable,
        Duration cacheTtl) {
    public AuthorizationDecision {
        decisionId = requireText(decisionId, "decisionId");
        Objects.requireNonNull(effect, "effect");
        mode = mode == null ? AuthorizationDecisionMode.FORMAL : mode;
        permissionCode = requireText(permissionCode, "permissionCode");
        Objects.requireNonNull(resourceRef, "resourceRef");
        grantedVisibility = grantedVisibility == null ? VisibilityLevel.NONE : grantedVisibility;
        matchedRoleBindingIds = copy(matchedRoleBindingIds);
        matchedScopeGrantIds = copy(matchedScopeGrantIds);
        matchedParticipantIds = copy(matchedParticipantIds);
        matchedOwnershipEvidence = copy(matchedOwnershipEvidence);
        matchedDenyIds = copy(matchedDenyIds);
        matchedScopeSources = copy(matchedScopeSources);
        policyVersion = policyVersion == null ? PolicyVersion.ZERO : policyVersion;
        securityEpoch = securityEpoch == null ? SecurityEpoch.ZERO : securityEpoch;
        descriptorHash = descriptorHash == null ? "" : descriptorHash.trim();
        runtimeLeaseId = runtimeLeaseId == null ? "" : runtimeLeaseId.trim();
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        cacheTtl = cacheTtl == null ? Duration.ZERO : cacheTtl;
        if (cacheTtl.isNegative()) throw new IllegalArgumentException("cacheTtl must not be negative");
        if (effect != DecisionEffect.ALLOW && grantedVisibility != VisibilityLevel.NONE) throw new IllegalArgumentException("non-ALLOW decisions cannot grant visibility");
        if (!cacheable && !cacheTtl.isZero()) throw new IllegalArgumentException("non-cacheable decision must use zero cacheTtl");
        if (mode != AuthorizationDecisionMode.FORMAL && !shadowOnly)
            throw new IllegalArgumentException("explain/simulation/shadow decisions must be non-executable evidence");
    }
    private static Set<String> copy(Set<String> values) { return values == null ? Set.of() : Set.copyOf(values); }
    private static String requireText(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
}
