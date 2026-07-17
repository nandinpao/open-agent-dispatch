package com.opensocket.aievent.core.assignment;

/**
 * Canonical TODO 15-C eligibility result for turning a routing decision into an offer.
 */
public enum DispatchEligibilityStatus {
    ELIGIBLE,
    ROUTING_NOT_SELECTED,
    AGENT_NOT_FOUND,
    AGENT_NOT_ASSIGNABLE,
    MISSING_AGENT_ID,
    MISSING_GATEWAY_NODE,
    MISSING_SESSION,
    SITE_MISMATCH,
    BACKEND_PROFILE_MISMATCH,
    CAPACITY_UNAVAILABLE,
    TASK_NOT_DISPATCHABLE
}
