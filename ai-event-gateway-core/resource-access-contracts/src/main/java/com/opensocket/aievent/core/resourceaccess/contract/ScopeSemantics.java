package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Objects;

/** Canonical RS0 classification for the richer Resource Access {@link ScopeType} vocabulary. */
public final class ScopeSemantics {
    private ScopeSemantics() {}

    public static ScopeSemanticClass classify(ScopeType scopeType) {
        Objects.requireNonNull(scopeType, "scopeType");
        return switch (scopeType) {
            case TENANT, DEPARTMENT, DEPARTMENT_SUBTREE, GROUP -> ScopeSemanticClass.ORGANIZATION_AUTHORITY;
            case RESOURCE -> ScopeSemanticClass.RESOURCE_EXCEPTION;
            case RESOURCE_TREE, TASK_CHAIN, EXPLICIT_SET -> ScopeSemanticClass.DOMAIN_RELATIONSHIP;
            case PARTICIPANT, OWNER, CREATED_BY_ME, ASSIGNED_TO_ME, AUDIT_WINDOW -> ScopeSemanticClass.CONTEXTUAL_CONSTRAINT;
        };
    }

    /** Only organization authority is eligible to express the IAM/RBAC scope ceiling. */
    public static boolean mayExpressOrganizationAuthority(ScopeType scopeType) {
        return classify(scopeType) == ScopeSemanticClass.ORGANIZATION_AUTHORITY;
    }

    /** Resource exceptions are narrow exceptions; they must never broaden an organization Role Binding ceiling. */
    public static boolean isNarrowResourceException(ScopeType scopeType) {
        return classify(scopeType) == ScopeSemanticClass.RESOURCE_EXCEPTION;
    }
}
