package com.opensocket.aievent.core.dispatch;

/** Canonical recovery action contributed by an authority-specific recovery adapter. */
public enum DispatchRecoveryAuthorityAction {
    DEFAULT,
    HOLD_DELIVERY_UNKNOWN,
    CONFIRM_DELIVERED
}
