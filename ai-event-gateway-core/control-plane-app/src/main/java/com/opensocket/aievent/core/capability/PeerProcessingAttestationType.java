package com.opensocket.aievent.core.capability;

/** Provenance class for a peer processing/storage-region attestation. */
public enum PeerProcessingAttestationType {
    SELF_ATTESTED,
    THIRD_PARTY_ATTESTED,
    PLATFORM_VERIFIED;

    public static PeerProcessingAttestationType require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("attestationType is required");
        try { return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_TYPE_INVALID"); }
    }
}
