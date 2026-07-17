package com.opensocket.aievent.core.routing.governance;

/** Source-agnostic routing strategy selected by persisted Dispatch Flow data. */
public enum GenericRoutingStrategy {
    LEGACY,
    CAPABILITY_FIRST,
    LOWEST_LOAD,
    ROUND_ROBIN,
    LOCAL_FIRST,
    WEIGHTED_SCORE,
    MANUAL_REVIEW
}
