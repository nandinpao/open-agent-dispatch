package com.opensocket.aievent.core.enforcement.activation.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class StableCohortHasher {
    public int bucketBasisPoints(String routeCanonicalValue, String cohortKey) {
        if (routeCanonicalValue == null || routeCanonicalValue.isBlank()) throw new IllegalArgumentException("routeCanonicalValue is required");
        if (cohortKey == null || cohortKey.isBlank()) throw new IllegalArgumentException("cohortKey is required");
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest((routeCanonicalValue + "|" + cohortKey).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        long value = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
        return (int)Math.floorMod(value, 10_000L);
    }
}
