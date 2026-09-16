package com.opensocket.aievent.core.capability;

/**
 * C0-B1 multidimensional trust evidence. These facts are inputs to the current
 * TrustAssurancePolicy; none of them is itself an authorization grant.
 */
public enum PeerTrustEvidenceType {
    CARD_SIGNATURE_VERIFIED,
    MTLS_CHANNEL_BOUND,
    PRIVATE_NETWORK_ATTESTED,
    MANUAL_APPROVAL,
    CONFORMANCE_CERTIFIED,
    PROCESSING_ATTESTED;

    public static PeerTrustEvidenceType require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("evidenceType is required");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A2A_PEER_TRUST_EVIDENCE_TYPE_UNSUPPORTED: " + value, ex);
        }
    }
}
