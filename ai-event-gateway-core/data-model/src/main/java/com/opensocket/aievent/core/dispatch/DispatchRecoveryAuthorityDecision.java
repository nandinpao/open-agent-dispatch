package com.opensocket.aievent.core.dispatch;

/** Immutable authority decision used when an expired dispatch claim is reconciled. */
public record DispatchRecoveryAuthorityDecision(
        boolean applicable,
        DispatchRecoveryAuthorityAction action,
        String reasonCode,
        String message) {

    public static DispatchRecoveryAuthorityDecision notApplicable() {
        return new DispatchRecoveryAuthorityDecision(false, DispatchRecoveryAuthorityAction.DEFAULT, "NOT_APPLICABLE", "No authority-specific recovery applies");
    }

    public static DispatchRecoveryAuthorityDecision holdUnknown(String reasonCode, String message) {
        return new DispatchRecoveryAuthorityDecision(true, DispatchRecoveryAuthorityAction.HOLD_DELIVERY_UNKNOWN, reasonCode, message);
    }

    public static DispatchRecoveryAuthorityDecision confirmDelivered(String reasonCode, String message) {
        return new DispatchRecoveryAuthorityDecision(true, DispatchRecoveryAuthorityAction.CONFIRM_DELIVERED, reasonCode, message);
    }
}
