package com.opensocket.aievent.core.iam.persistence.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthenticationCryptoAdapterTest {
    @Test
    void aesGcmRoundTripBindsCiphertextToKeyId() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) key[index] = (byte) index;
        AesGcmMfaSecretProtector protector = new AesGcmMfaSecretProtector("iam-mfa-v1", key);

        var protectedSecret = protector.protect("JBSWY3DPEHPK3PXP");

        assertEquals("JBSWY3DPEHPK3PXP",
                protector.reveal(protectedSecret.protectedValue(), protectedSecret.keyId()));
        assertThrows(IllegalStateException.class,
                () -> protector.reveal(protectedSecret.protectedValue(), "iam-mfa-v2"));
    }

    @Test
    void hmacOneTimeSecretUsesPepperAndConstantTimeComparison() {
        byte[] pepper = new byte[32];
        for (int index = 0; index < pepper.length; index++) pepper[index] = (byte) (index + 1);
        HmacOneTimeSecretAdapter adapter = new HmacOneTimeSecretAdapter(pepper);

        String hash = adapter.hash("one-time-value");

        assertTrue(adapter.matches("one-time-value", hash));
        assertFalse(adapter.matches("other-value", hash));
    }

    @Test
    void totpMatchesRfc6238Sha1Vector() {
        Rfc6238TotpVerificationAdapter adapter = new Rfc6238TotpVerificationAdapter(0);
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

        assertTrue(adapter.verify(secret, "94287082", Instant.ofEpochSecond(59),
                8, 30, "HmacSHA1"));
        assertFalse(adapter.verify(secret, "94287081", Instant.ofEpochSecond(59),
                8, 30, "HmacSHA1"));
    }
}
