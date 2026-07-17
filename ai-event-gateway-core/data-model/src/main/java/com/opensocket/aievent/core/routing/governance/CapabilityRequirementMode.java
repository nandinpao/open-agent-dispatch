package com.opensocket.aievent.core.routing.governance;

/**
 * LEGACY preserves the current runtime contract during P1.
 * The remaining values become executable only when the P2 resolver is enabled.
 */
public enum CapabilityRequirementMode {
    LEGACY,
    EXPLICIT,
    SOURCE_DEFAULT,
    NONE
}
