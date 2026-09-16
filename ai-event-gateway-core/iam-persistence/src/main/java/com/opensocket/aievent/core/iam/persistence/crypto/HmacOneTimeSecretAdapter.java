package com.opensocket.aievent.core.iam.persistence.crypto;

import com.opensocket.aievent.core.iam.authentication.application.port.out.OneTimeSecretPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacOneTimeSecretAdapter implements OneTimeSecretPort {
    private final SecureRandom random = new SecureRandom();
    private final byte[] pepper;
    public HmacOneTimeSecretAdapter(byte[] pepper) {
        if (pepper == null || pepper.length < 32) throw new IllegalArgumentException("secret pepper must contain at least 32 bytes");
        this.pepper = pepper.clone();
    }
    @Override public String generate(int bytes) {
        if (bytes < 16 || bytes > 128) throw new IllegalArgumentException("secret size must be 16..128 bytes");
        byte[] value = new byte[bytes]; random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    @Override public String hash(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(value)); }
    @Override public boolean matches(String value,String hash) {
        if (value == null || hash == null) return false;
        return MessageDigest.isEqual(mac(value), Base64.getUrlDecoder().decode(hash));
    }
    private byte[] mac(String value) {
        try { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(pepper,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
        catch(Exception e){throw new IllegalStateException("Unable to hash one-time secret",e);}
    }
}
