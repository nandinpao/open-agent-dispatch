package com.opensocket.aievent.core.resourceaccess.contract;

/** Canonical server-side list strategy for organization/resource scoped business data. */
public enum ResourceListScopeQueryStrategy {
    DENY_ALL,
    TENANT,
    DIRECT_HIERARCHY_JOIN,
    EXPLICIT_RESOURCE_SET,
    HYBRID
}
