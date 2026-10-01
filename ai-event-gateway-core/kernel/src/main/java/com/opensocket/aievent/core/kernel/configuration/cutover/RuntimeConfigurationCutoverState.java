package com.opensocket.aievent.core.kernel.configuration.cutover;

/** Durable two-phase Runtime Configuration authority cutover lifecycle. */
public enum RuntimeConfigurationCutoverState {
    PREPARED,
    FINALIZED,
    CANCELLED
}
