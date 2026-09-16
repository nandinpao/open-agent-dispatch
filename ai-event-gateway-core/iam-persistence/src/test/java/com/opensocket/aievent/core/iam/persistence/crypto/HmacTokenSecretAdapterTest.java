package com.opensocket.aievent.core.iam.persistence.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import org.junit.jupiter.api.Test;

class HmacTokenSecretAdapterTest {
    @Test
    void generatedTokenHasUnambiguousPrefixAndStoresOnlyHmacMetadata() {
        byte[] pepper = new byte[32];
        for (int index = 0; index < pepper.length; index++) pepper[index] = (byte) (index + 11);
        HmacTokenSecretAdapter adapter = new HmacTokenSecretAdapter(pepper);

        var generated = adapter.generate(AccessTokenType.SERVICE_ACCOUNT_TOKEN);

        assertTrue(generated.publicPrefix().matches("odp_sat_[0-9a-f]{18}"));
        assertTrue(generated.fullToken().startsWith(generated.publicPrefix() + "_"));
        String secret = generated.fullToken().substring(generated.publicPrefix().length() + 1);
        assertTrue(adapter.matches(secret, generated.hash()));
        assertFalse(adapter.matches(secret + "x", generated.hash()));
        assertFalse(generated.hash().encoded().contains(secret));
    }
}
