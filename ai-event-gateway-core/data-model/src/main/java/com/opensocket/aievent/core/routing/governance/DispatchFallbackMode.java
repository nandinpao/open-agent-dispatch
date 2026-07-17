package com.opensocket.aievent.core.routing.governance;

/** Behavior to use when a Flow does not declare an explicit Capability. */
public enum DispatchFallbackMode {
    SOURCE_BASELINE,
    EXPLICIT_ONLY,
    BLOCK
}
