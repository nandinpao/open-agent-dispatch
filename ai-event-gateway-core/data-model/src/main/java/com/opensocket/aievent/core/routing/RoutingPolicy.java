package com.opensocket.aievent.core.routing;

public enum RoutingPolicy {
    MANUAL_REVIEW,
    LOCAL_ONLY,
    LOCAL_FIRST,
    GLOBAL_AVAILABLE_FIRST,
    /** Dispatch Flow -> Flow-owned Rule -> requested Capability -> Agent. */
    FLOW_RULE,
    CAPABILITY_FIRST,
    LOAD_BALANCED
}
