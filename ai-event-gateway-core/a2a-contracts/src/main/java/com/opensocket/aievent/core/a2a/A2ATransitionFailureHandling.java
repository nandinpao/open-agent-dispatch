package com.opensocket.aievent.core.a2a;

public enum A2ATransitionFailureHandling {
    REJECT_COMMAND,
    ROLLBACK,
    RETRY_SAFE,
    QUARANTINE,
    RECONCILE,
    WAIT_HUMAN,
    TERMINAL_FAILURE
}
