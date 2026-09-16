package com.opensocket.aievent.core.iam.persistence.crypto;

import com.opensocket.aievent.core.iam.authentication.application.port.out.MfaSecretProtectorPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Envelope-ready AES-256-GCM protector. The key id is persisted with each MFA method. */
public final class AesGcmMfaSecretProtector implements MfaSecretProtectorPort {
    private final String keyId; private final SecretKeySpec key; private final SecureRandom random = new SecureRandom();
    public AesGcmMfaSecretProtector(String keyId, byte[] keyBytes) {
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId is required");
        if (keyBytes == null || keyBytes.length != 32) throw new IllegalArgumentException("AES-GCM key must contain 32 bytes");
        this.keyId=keyId.trim();this.key=new SecretKeySpec(keyBytes,"AES");
    }
    @Override public ProtectedSecret protect(String plaintext) {
        try { byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));c.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));byte[] encrypted=c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));ByteBuffer b=ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted);return new ProtectedSecret(Base64.getUrlEncoder().withoutPadding().encodeToString(b.array()),keyId); }
        catch(Exception e){throw new IllegalStateException("Unable to protect MFA secret",e);}
    }
    @Override public String reveal(String protectedValue,String storedKeyId) {
        if(!keyId.equals(storedKeyId))throw new IllegalStateException("MFA secret key is not available: "+storedKeyId);
        try { byte[] all=Base64.getUrlDecoder().decode(protectedValue);if(all.length<29)throw new IllegalArgumentException("Protected MFA secret is invalid");byte[] iv=java.util.Arrays.copyOfRange(all,0,12);byte[] encrypted=java.util.Arrays.copyOfRange(all,12,all.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));c.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));return new String(c.doFinal(encrypted),StandardCharsets.UTF_8); }
        catch(Exception e){throw new IllegalStateException("Unable to reveal MFA secret",e);}
    }
}
