package com.opensocket.aievent.core.dispatch;

public enum RedispatchAction {
    IMMEDIATE_REDISPATCH,
    RETRY_WAIT,
    FAILED,
    ESCALATED,
    DEAD_LETTER
}
