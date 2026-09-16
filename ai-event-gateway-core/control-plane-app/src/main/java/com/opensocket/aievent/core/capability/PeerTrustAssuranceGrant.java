package com.opensocket.aievent.core.capability;

/** Computed external-A2A assurance grant. Grants are policy outputs, never administrator-assigned trust levels. */
public enum PeerTrustAssuranceGrant {
    READ_ALLOWED,
    SENSITIVE_READ_ALLOWED,
    WRITE_ALLOWED,
    CRITICAL_ALLOWED;

    public static PeerTrustAssuranceGrant require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("grantType is required");
        try { return valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_GRANT_INVALID"); }
    }
}
