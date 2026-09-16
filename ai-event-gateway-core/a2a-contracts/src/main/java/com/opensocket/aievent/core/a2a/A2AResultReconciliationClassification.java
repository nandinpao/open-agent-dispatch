package com.opensocket.aievent.core.a2a;

public enum A2AResultReconciliationClassification {
    NONE,
    CHILD_COMPLETION_MISSING,
    PARENT_AGGREGATION_MISSING,
    AGGREGATION_CAS_CONFLICT,
    POLICY_BINDING_INVALID,
    PROCESSING_ERROR,
    RETRY_EXHAUSTED
}
