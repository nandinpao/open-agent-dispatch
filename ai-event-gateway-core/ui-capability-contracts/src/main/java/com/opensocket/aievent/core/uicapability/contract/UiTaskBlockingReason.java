package com.opensocket.aievent.core.uicapability.contract;

/** Human-facing Phase 7C Task blocking categories. These are projection labels, not domain state authority. */
public enum UiTaskBlockingReason {
    WAITING_FOR_AGENT,
    NO_ELIGIBLE_AGENT,
    NO_SERVICE_SCOPE,
    WAITING_APPROVAL,
    WAITING_CONTEXT,
    PROJECTION_FAILED,
    PERMISSION_REQUIRED,
    RESOURCE_LOCKED,
    MANUAL_REVIEW_REQUIRED
}
