package com.opensocket.aievent.core.iam.persistence.crypto;

import com.opensocket.aievent.core.iam.token.application.port.out.MachineSigningKeyProtectorPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM protector for persisted machine JWT private keys. */
public final class AesGcmMachineSigningKeyProtector implements MachineSigningKeyProtectorPort {
    private final String keyId;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmMachineSigningKeyProtector(String keyId, byte[] keyBytes) {
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("machine signing protection key id is required");
        if (keyBytes == null || keyBytes.length != 32) throw new IllegalArgumentException("machine signing protection key must contain 32 bytes");
        this.keyId = keyId.trim();
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
    }

    @Override
    public ProtectedKey protect(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("private key material is required");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad());
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return new ProtectedKey(Base64.getUrlEncoder().withoutPadding().encodeToString(combined), keyId);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Machine signing key protection failed", e);
        }
    }

    @Override
    public String reveal(String protectedValue, String protectionKeyId) {
        if (!keyId.equals(protectionKeyId)) throw new IllegalArgumentException("Unknown machine signing protection key id");
        try {
            byte[] all = Base64.getUrlDecoder().decode(protectedValue);
            if (all.length < 29) throw new IllegalArgumentException("Protected machine signing key is invalid");
            byte[] iv = java.util.Arrays.copyOfRange(all, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(all, 12, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad());
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Machine signing key reveal failed", e);
        }
    }

    private byte[] aad() {
        return ("opendispatch:machine-jwt-signing-key:" + keyId).getBytes(StandardCharsets.UTF_8);
    }
}
