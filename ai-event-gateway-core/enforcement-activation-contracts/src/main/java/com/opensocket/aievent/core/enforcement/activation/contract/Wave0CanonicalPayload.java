package com.opensocket.aievent.core.enforcement.activation.contract;

/** A read-pilot payload must provide a deterministic, secret-free representation for comparison. */
public interface Wave0CanonicalPayload {
    String canonicalValue();

    /**
     * Compares this Legacy/reference payload with the Target payload. Domain payloads may override
     * this method when equality requires stronger semantics such as deterministic ordering.
     */
    default Wave0ReadPilotMismatchCategory compareTarget(Wave0CanonicalPayload target) {
        if (target == null) return Wave0ReadPilotMismatchCategory.TARGET_ERROR;
        return canonicalValue().equals(target.canonicalValue())
                ? Wave0ReadPilotMismatchCategory.MATCH
                : Wave0ReadPilotMismatchCategory.PAYLOAD_MISMATCH;
    }
}
