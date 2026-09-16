package com.opensocket.aievent.core.integration.issue.policy;

/** Result of resolving a governed Integration Project Mapping for an Issue policy decision. */
public enum IssuePolicyBindingStatus {
    NOT_REQUIRED,
    NOT_EVALUATED,
    RESOLVED,
    MAPPING_NOT_FOUND,
    MAPPING_AMBIGUOUS,
    MAPPING_NOT_ACTIVE,
    MAPPING_SCHEMA_UNAVAILABLE,
    CONNECTION_UNAVAILABLE
}
