package com.opensocket.aievent.core.uicapability.core;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** Opaque nonce. It is not an access token and cannot authorize a mutation. */
public final class SecureRandomUiHydrationNonce implements UiHydrationNoncePort {
    private final SecureRandom random;
    public SecureRandomUiHydrationNonce() { this(new SecureRandom()); }
    SecureRandomUiHydrationNonce(SecureRandom random) { this.random = Objects.requireNonNull(random); }
    @Override public String issue(String tenantId, String principalId, String routeContext, Instant expiresAt) {
        byte[] value = new byte[24]; random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
