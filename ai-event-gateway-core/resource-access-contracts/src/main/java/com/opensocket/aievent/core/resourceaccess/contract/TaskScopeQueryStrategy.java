package com.opensocket.aievent.core.resourceaccess.contract;

public enum TaskScopeQueryStrategy {
    DENY_ALL,
    TENANT,
    DIRECT_HIERARCHY_JOIN,
    EXPLICIT_RESOURCE_SET,
    PARTICIPANT_JOIN,
    HYBRID
}
