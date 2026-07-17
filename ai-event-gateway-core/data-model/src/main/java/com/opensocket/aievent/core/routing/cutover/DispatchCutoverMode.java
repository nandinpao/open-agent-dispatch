package com.opensocket.aievent.core.routing.cutover;

/** Runtime authority mode for the generic dispatch stack. */
public enum DispatchCutoverMode {
    SHADOW,
    CANARY,
    AUTHORITATIVE,
    ROLLED_BACK,
    DISABLED
}
