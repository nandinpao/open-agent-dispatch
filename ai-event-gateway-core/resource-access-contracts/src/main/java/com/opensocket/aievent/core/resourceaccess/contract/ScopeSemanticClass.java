package com.opensocket.aievent.core.resourceaccess.contract;

/** RS0 classification that prevents contextual/domain matching strategies from becoming Role Binding scopes. */
public enum ScopeSemanticClass {
    ORGANIZATION_AUTHORITY,
    RESOURCE_EXCEPTION,
    DOMAIN_RELATIONSHIP,
    CONTEXTUAL_CONSTRAINT
}
