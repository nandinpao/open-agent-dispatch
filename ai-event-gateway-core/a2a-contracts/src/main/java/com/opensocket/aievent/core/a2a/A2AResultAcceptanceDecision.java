package com.opensocket.aievent.core.a2a;

/** Canonical classification for every submitted A2A result attempt. */
public enum A2AResultAcceptanceDecision {
    ACCEPTED,
    DUPLICATE,
    STALE,
    REVOKED,
    CONFLICT,
    LATE,
    MISSING_EVIDENCE
}
