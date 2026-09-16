package com.opensocket.aievent.core.capability;

/** Subject to which a trust-evidence fact applies. */
public enum PeerTrustEvidenceSubjectType {
    PEER,
    INTERFACE;

    public static PeerTrustEvidenceSubjectType require(String value) {
        if (value == null || value.isBlank()) return PEER;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A2A_PEER_TRUST_EVIDENCE_SUBJECT_UNSUPPORTED: " + value, ex);
        }
    }
}
