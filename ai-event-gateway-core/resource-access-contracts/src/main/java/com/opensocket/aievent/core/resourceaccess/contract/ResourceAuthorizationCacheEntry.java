package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Prewarm entry bound to the exact policy and security epoch namespace in its key. */
public record ResourceAuthorizationCacheEntry(
        ResourceAuthorizationCacheKey key,
        AuthorizationDecision decision,
        Instant expiresAt) {
    public ResourceAuthorizationCacheEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (decision.effect() != DecisionEffect.ALLOW || decision.mode() != AuthorizationDecisionMode.FORMAL
                || decision.shadowOnly() || !decision.cacheable()) {
            throw new IllegalArgumentException("prewarm requires cacheable executable FORMAL ALLOW");
        }
        if (!decision.securityEpoch().equals(key.securityEpoch()) || !decision.policyVersion().equals(key.policyVersion())) {
            throw new IllegalArgumentException("prewarm entry namespace mismatch");
        }
    }
}
