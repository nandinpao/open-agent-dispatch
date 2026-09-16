package com.opensocket.aievent.core.capability;

/** Durable lifecycle state. EXPIRED is an effective read state, not a stored mutation. */
public enum PeerProcessingAttestationStatus {
    PENDING,
    VERIFIED,
    REVOKED
}
