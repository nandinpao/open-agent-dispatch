package com.opensocket.aievent.core.a2a;

public enum A2AResultAggregationPolicy {
    ALL_SUCCESS,
    ANY_SUCCESS,
    QUORUM,
    PARTIAL_ALLOWED,
    MANUAL_DECISION,
    FAIL_FAST,
    /** @deprecated compatibility alias for ALL_SUCCESS. */
    @Deprecated WAIT_ALL,
    /** @deprecated compatibility alias for ANY_SUCCESS. */
    @Deprecated WAIT_ANY,
    /** @deprecated compatibility alias for MANUAL_DECISION. */
    @Deprecated MANUAL_REVIEW,
    /** @deprecated compatibility alias for PARTIAL_ALLOWED. */
    @Deprecated IGNORE_CHILD_FAILURE,
    /** @deprecated compatibility alias for FAIL_FAST. */
    @Deprecated FAIL_ON_ANY_CHILD_FAILURE;

    public A2AResultAggregationPolicy canonical() {
        return switch (this) {
            case WAIT_ALL -> ALL_SUCCESS;
            case WAIT_ANY -> ANY_SUCCESS;
            case MANUAL_REVIEW -> MANUAL_DECISION;
            case IGNORE_CHILD_FAILURE -> PARTIAL_ALLOWED;
            case FAIL_ON_ANY_CHILD_FAILURE -> FAIL_FAST;
            default -> this;
        };
    }
}
