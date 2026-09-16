package com.opensocket.aievent.core.task.authority;

/** Finalization evidence durability requirement. */
public enum EvidenceAvailabilityRequirement {
    EVIDENCE_REQUIRED_BEFORE_EXECUTION, EVIDENCE_REQUIRED_BEFORE_COMMIT, BUFFER_ALLOWED, BEST_EFFORT
}
