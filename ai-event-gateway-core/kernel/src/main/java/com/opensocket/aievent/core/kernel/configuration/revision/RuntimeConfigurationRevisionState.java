package com.opensocket.aievent.core.kernel.configuration.revision;

/** V40-3 authoritative revision lifecycle. Application state is intentionally separate. */
public enum RuntimeConfigurationRevisionState {
    DRAFT,
    VALIDATED,
    PENDING_APPROVAL,
    APPROVED,
    PUBLISHED,
    SUPERSEDED,
    REJECTED,
    CANCELLED;

    public boolean terminal() {
        return this == SUPERSEDED || this == REJECTED || this == CANCELLED;
    }

    public boolean authoritative() {
        return this == PUBLISHED;
    }
}
