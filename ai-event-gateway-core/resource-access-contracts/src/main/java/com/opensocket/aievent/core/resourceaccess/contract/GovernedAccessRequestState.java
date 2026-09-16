package com.opensocket.aievent.core.resourceaccess.contract;

/** Lifecycle of a governed temporary-access request. The matching Scope Grant remains canonical policy state. */
public enum GovernedAccessRequestState {
    DRAFT,
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED,
    CANCELLED,
    EXPIRED
}
