package com.opensocket.aievent.core.iam.token.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Persisted instance-level machine JWT signing key metadata.
 *
 * <p>The private key is always protected before crossing the persistence boundary. Clear private
 * key material is never represented by this aggregate.</p>
 */
public record MachineSigningKey(
        String keyId,
        String algorithm,
        String publicKeyDerBase64,
        String protectedPrivateKey,
        String protectionKeyId,
        MachineSigningKeyStatus status,
        Instant activatedAt,
        Instant rotateAfter,
        Instant verifyUntil,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public MachineSigningKey {
        keyId = required(keyId, "keyId", 128);
        algorithm = required(algorithm, "algorithm", 32);
        if (!"RS256".equals(algorithm)) throw new IllegalArgumentException("Only RS256 signing keys are supported");
        publicKeyDerBase64 = required(publicKeyDerBase64, "publicKeyDerBase64", 8192);
        protectedPrivateKey = required(protectedPrivateKey, "protectedPrivateKey", 16384);
        protectionKeyId = required(protectionKeyId, "protectionKeyId", 128);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(activatedAt, "activatedAt");
        Objects.requireNonNull(rotateAfter, "rotateAfter");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!rotateAfter.isAfter(activatedAt)) throw new IllegalArgumentException("rotateAfter must be after activatedAt");
        if (status == MachineSigningKeyStatus.VERIFY_ONLY && verifyUntil == null) {
            throw new IllegalArgumentException("VERIFY_ONLY signing keys require verifyUntil");
        }
        if (verifyUntil != null && verifyUntil.isBefore(activatedAt)) {
            throw new IllegalArgumentException("verifyUntil must not be before activatedAt");
        }
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    public boolean needsRotation(Instant at) {
        return status != MachineSigningKeyStatus.ACTIVE || !Objects.requireNonNull(at, "at").isBefore(rotateAfter);
    }

    public boolean publishableAt(Instant at) {
        if (status == MachineSigningKeyStatus.ACTIVE) return true;
        return status == MachineSigningKeyStatus.VERIFY_ONLY && verifyUntil != null && at.isBefore(verifyUntil);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }
}
