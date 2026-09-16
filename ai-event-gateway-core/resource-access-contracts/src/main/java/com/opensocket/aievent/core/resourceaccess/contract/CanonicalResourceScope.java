package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Locale;
import java.util.Optional;

/**
 * RS0 frozen authorization scope vocabulary shared by IAM/RBAC and Resource Access.
 *
 * <p>The vocabulary is intentionally smaller than {@link ScopeType}. ScopeType also contains
 * domain/contextual matching strategies (for example PARTICIPANT or TASK_CHAIN); those strategies
 * are not valid Role Binding scope kinds and must not become a second RBAC authority.</p>
 *
 * <p>RESOURCE is part of the canonical authorization vocabulary, but in the v19 baseline it is a
 * narrow explicit Resource Access exception rather than an IAM Role Binding scope. Promoting it to
 * an IAM Role Binding scope requires a later compatibility/migration decision; RS0 does not silently
 * change existing RBAC persistence semantics.</p>
 */
public enum CanonicalResourceScope {
    INSTANCE(true, false, false),
    TENANT(true, true, true),
    DEPARTMENT(true, true, true),
    DEPARTMENT_SUBTREE(true, true, true),
    GROUP(true, true, true),
    RESOURCE(false, true, false);

    private final boolean roleBindingEligible;
    private final boolean resourceAccessEligible;
    private final boolean organizationScope;

    CanonicalResourceScope(boolean roleBindingEligible, boolean resourceAccessEligible, boolean organizationScope) {
        this.roleBindingEligible = roleBindingEligible;
        this.resourceAccessEligible = resourceAccessEligible;
        this.organizationScope = organizationScope;
    }

    public boolean roleBindingEligible() {
        return roleBindingEligible;
    }

    public boolean resourceAccessEligible() {
        return resourceAccessEligible;
    }

    public boolean organizationScope() {
        return organizationScope;
    }

    public static Optional<CanonicalResourceScope> fromRoleBindingScopeName(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            CanonicalResourceScope scope = valueOf(value.trim().toUpperCase(Locale.ROOT));
            return scope.roleBindingEligible ? Optional.of(scope) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<CanonicalResourceScope> fromResourceAccessScope(ScopeType scopeType) {
        if (scopeType == null) return Optional.empty();
        return switch (scopeType) {
            case TENANT -> Optional.of(TENANT);
            case DEPARTMENT -> Optional.of(DEPARTMENT);
            case DEPARTMENT_SUBTREE -> Optional.of(DEPARTMENT_SUBTREE);
            case GROUP -> Optional.of(GROUP);
            case RESOURCE -> Optional.of(RESOURCE);
            case RESOURCE_TREE, TASK_CHAIN, PARTICIPANT, OWNER, CREATED_BY_ME,
                    ASSIGNED_TO_ME, AUDIT_WINDOW, EXPLICIT_SET -> Optional.empty();
        };
    }
}
