package com.opensocket.aievent.core.dispatch;

/**
 * Phase 1 dispatch execution policy. Dispatch review is not the normal control point;
 * eligible assignments are queued for gateway delivery unless the operator explicitly pauses delivery.
 */
public enum DispatchExecutionPolicy {
    AUTO_AFTER_ASSIGNMENT,
    PAUSED,
    MANUAL_HOLD;

    public boolean autoExecutes() {
        return this == AUTO_AFTER_ASSIGNMENT;
    }
}
