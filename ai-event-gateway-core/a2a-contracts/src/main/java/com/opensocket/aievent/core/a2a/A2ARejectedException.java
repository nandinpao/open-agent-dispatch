package com.opensocket.aievent.core.a2a;

/** Canonical A2A policy/identity rejection carrying a stable reason code across adapters. */
public final class A2ARejectedException extends IllegalArgumentException {
    private final A2AReasonCode reasonCode;

    public A2ARejectedException(A2AReasonCode reasonCode, String message) {
        super((reasonCode == null ? "A2A_REJECTED" : reasonCode.name()) + ": " + message);
        if (reasonCode == null) throw new IllegalArgumentException("reasonCode is required");
        this.reasonCode = reasonCode;
    }

    public A2AReasonCode reasonCode() {
        return reasonCode;
    }
}
