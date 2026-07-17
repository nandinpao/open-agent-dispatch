package com.opensocket.aievent.core.callback;

/** Decision returned by a callback acceptance guard. */
public record TaskCallbackGuardDecision(boolean allowed, String reasonCode, String message) {

    public static TaskCallbackGuardDecision allow() {
        return new TaskCallbackGuardDecision(true, null, null);
    }

    public static TaskCallbackGuardDecision block(String reasonCode, String message) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode is required for a blocked callback");
        }
        return new TaskCallbackGuardDecision(false, reasonCode, message);
    }
}
