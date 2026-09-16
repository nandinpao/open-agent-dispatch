package com.opensocket.aievent.core.iam.persistence.crypto;

import com.opensocket.aievent.core.iam.token.application.port.out.ServiceAccountCredentialSecretPort;
import com.opensocket.aievent.core.iam.token.application.port.out.TokenSecretPort;
import com.opensocket.aievent.core.iam.token.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Shared HMAC secret hashing primitive for opaque access tokens and Service Account client secrets. */
public final class HmacTokenSecretAdapter implements TokenSecretPort, ServiceAccountCredentialSecretPort {
    private final byte[] pepper;
    private final SecureRandom random = new SecureRandom();

    public HmacTokenSecretAdapter(byte[] pepper) {
        if (pepper == null || pepper.length < 32) throw new IllegalArgumentException("token pepper must be at least 32 bytes");
        this.pepper = pepper.clone();
    }

    @Override
    public GeneratedSecret generate(AccessTokenType type) {
        SecretMaterial material = material(type.prefix());
        String full = material.publicId() + "_" + material.secret();
        return new GeneratedSecret(UUID.randomUUID().toString(), material.publicId(), full, material.last4(), material.hash());
    }

    @Override
    public ServiceAccountCredentialSecretPort.GeneratedCredential generate() {
        SecretMaterial material = material("sac");
        return new ServiceAccountCredentialSecretPort.GeneratedCredential(
                UUID.randomUUID().toString(), material.publicId(), material.secret(), material.last4(), material.hash());
    }

    @Override
    public boolean matches(String secret, TokenHash hash) {
        if (secret == null || hash == null || !"HMAC-SHA-256".equals(hash.algorithm())) return false;
        return MessageDigest.isEqual(
                hash(secret).getBytes(StandardCharsets.US_ASCII),
                hash.encoded().getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public void constantTimeReject(String presentedSecret) {
        String value = presentedSecret == null ? "" : presentedSecret;
        String actual = hash(value);
        String dummy = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), dummy.getBytes(StandardCharsets.US_ASCII));
    }

    private SecretMaterial material(String kind) {
        byte[] publicBytes = new byte[9], secretBytes = new byte[32];
        random.nextBytes(publicBytes);
        random.nextBytes(secretBytes);
        String publicId = "odp_" + kind + "_" + HexFormat.of().formatHex(publicBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        Arrays.fill(publicBytes, (byte) 0);
        Arrays.fill(secretBytes, (byte) 0);
        return new SecretMaterial(publicId, secret, secret.substring(secret.length() - 4), new TokenHash("HMAC-SHA-256", hash(secret)));
    }

    private String hash(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private record SecretMaterial(String publicId, String secret, String last4, TokenHash hash) {}
}
