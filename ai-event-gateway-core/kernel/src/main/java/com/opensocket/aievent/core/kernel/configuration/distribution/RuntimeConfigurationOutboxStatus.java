package com.opensocket.aievent.core.kernel.configuration.distribution;

public enum RuntimeConfigurationOutboxStatus {
    PENDING,
    CLAIMED,
    DISTRIBUTED,
    FAILED,
    SUPERSEDED
}
