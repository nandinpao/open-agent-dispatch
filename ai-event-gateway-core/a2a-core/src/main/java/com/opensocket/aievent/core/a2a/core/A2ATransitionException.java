package com.opensocket.aievent.core.a2a.core;

import com.opensocket.aievent.core.a2a.A2ATransitionFailureReason;

public final class A2ATransitionException extends IllegalStateException {
    private final A2ATransitionFailureReason reason;
    public A2ATransitionException(A2ATransitionFailureReason reason, String message) { super(message); this.reason = reason; }
    public A2ATransitionFailureReason reason() { return reason; }
}
