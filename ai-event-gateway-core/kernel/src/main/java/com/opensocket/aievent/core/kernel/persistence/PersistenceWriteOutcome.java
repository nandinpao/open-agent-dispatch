package com.opensocket.aievent.core.kernel.persistence;

public enum PersistenceWriteOutcome {
    APPLIED,
    NOT_FOUND,
    OWNERSHIP_LOST,
    CONFLICT
}
