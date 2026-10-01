package com.opensocket.aievent.core.kernel.configuration.distribution;

/**
 * Runtime-configuration convergence truth.
 * DISTRIBUTED is never equivalent to APPLIED. NOT_SEEN is a projection-only state for a
 * required topology target that has not yet produced apply-state evidence.
 */
public enum RuntimeConfigurationApplyStatus {
    NOT_SEEN,
    DESIRED,
    DISTRIBUTED,
    APPLIED,
    FAILED,
    STALE
}
