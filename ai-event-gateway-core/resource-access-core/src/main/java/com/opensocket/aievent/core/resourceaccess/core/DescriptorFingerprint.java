package com.opensocket.aievent.core.resourceaccess.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable SHA-256 helper used for projection evidence, not for secrets or authentication. */
public final class DescriptorFingerprint {
    private DescriptorFingerprint() {}
    public static String sha256(String canonicalValue) {
        try {
            byte[] value = canonicalValue == null ? new byte[0] : canonicalValue.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
