package com.opensocket.aievent.core.task.v53;

/** A0-R2 canonical Task business outcome authority; resolved during FINALIZING. */
public enum TaskOutcome {
    UNRESOLVED,
    SUCCEEDED,
    PARTIAL_SUCCEEDED,
    FAILED,
    CANCELLED
}
