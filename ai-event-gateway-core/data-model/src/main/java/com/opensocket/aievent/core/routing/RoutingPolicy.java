package com.opensocket.aievent.core.routing;

public enum RoutingPolicy {
    MANUAL_REVIEW,
    LOCAL_ONLY,
    LOCAL_FIRST,
    GLOBAL_AVAILABLE_FIRST,
    /** Dispatch Flow -> Flow-owned Rule/default Pool -> Agent. */
    FLOW_RULE,
    /** Upstream governance already resolved an authoritative Agent Pool (for example A2A Policy).
     * Dispatch may select an eligible Agent inside that Pool, but must not re-resolve or replace the Pool. */
    GOVERNED_POOL,
    CAPABILITY_FIRST,
    LOAD_BALANCED
}
