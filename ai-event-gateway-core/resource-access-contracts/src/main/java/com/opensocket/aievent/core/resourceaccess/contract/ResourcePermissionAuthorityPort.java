package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Set;

/** Bridge to the existing IAM RBAC authority. */
@FunctionalInterface
public interface ResourcePermissionAuthorityPort {
    ResourcePermissionDecision evaluate(AuthorizationRequest request, PrincipalScopeSnapshot principalScope);

    /**
     * RS1 list/search authority projection. Implementations should override this to preserve every
     * effective IAM Role Binding scope. The default keeps compatibility with older test adapters and
     * deliberately projects only the single point-decision scope.
     */
    default ResourcePermissionScopeDecision resolveEffectiveScopes(
            AuthorizationRequest request, PrincipalScopeSnapshot principalScope) {
        ResourcePermissionDecision decision = evaluate(request, principalScope);
        if (!decision.granted()) {
            return new ResourcePermissionScopeDecision(false, decision.reasonCode(), false,
                    Set.of(), Set.of(), Set.of(), decision.matchedBindingIds(), decision.matchedRoleIds());
        }
        boolean tenant = decision.tenantScoped();
        Set<String> exact = Set.of(), subtrees = Set.of(), groups = Set.of();
        if (!decision.effectiveScopeId().isBlank()) {
            switch (decision.effectiveScopeType()) {
                case "DEPARTMENT" -> exact = Set.of(decision.effectiveScopeId());
                case "DEPARTMENT_SUBTREE" -> subtrees = Set.of(decision.effectiveScopeId());
                case "GROUP" -> groups = Set.of(decision.effectiveScopeId());
                default -> { }
            }
        }
        return new ResourcePermissionScopeDecision(true, decision.reasonCode(), tenant,
                exact, subtrees, groups, decision.matchedBindingIds(), decision.matchedRoleIds());
    }
}
