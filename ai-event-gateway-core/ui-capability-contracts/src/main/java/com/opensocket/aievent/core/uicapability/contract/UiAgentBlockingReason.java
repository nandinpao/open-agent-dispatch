package com.opensocket.aievent.core.uicapability.contract;

/** Stable user-facing Agent blocking classification. */
public enum UiAgentBlockingReason {
    MISSING_PROFILE,
    NOT_APPROVED,
    DISABLED,
    QUARANTINED,
    CREDENTIAL_MISSING,
    CREDENTIAL_EXPIRED,
    RUNTIME_OFFLINE,
    NO_RUNTIME_BINDING,
    NO_SERVICE_SCOPE,
    NO_DISPATCH_FLOW,
    NO_AVAILABLE_SLOTS,
    RUNTIME_DRAINING,
    READY
}
