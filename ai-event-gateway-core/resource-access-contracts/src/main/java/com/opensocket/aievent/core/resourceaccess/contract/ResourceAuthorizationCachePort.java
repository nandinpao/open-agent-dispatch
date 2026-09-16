package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/** Cache is an optimization only; policy and epoch authorities remain the source of truth. */
public interface ResourceAuthorizationCachePort {
    Optional<AuthorizationDecision> get(ResourceAuthorizationCacheKey key, Instant now);
    void put(ResourceAuthorizationCacheKey key, AuthorizationDecision decision, Instant expiresAt);
    void invalidateTenant(String tenantId);
    void invalidatePrincipal(String tenantId,String principalId);
    void invalidateResource(ResourceRef resourceRef);

    /**
     * Coalesces concurrent misses for the same exact versioned key. Implementations must never reuse a
     * result across policy/security epoch namespaces. The default preserves compatibility without coordination.
     */
    default AuthorizationDecision coordinate(
            ResourceAuthorizationCacheKey key, Supplier<AuthorizationDecision> loader) {
        return loader.get();
    }

    /** Prewarms only already-authorized, executable FORMAL ALLOW entries. */
    default int prewarm(Collection<ResourceAuthorizationCacheEntry> entries) {
        if (entries == null) return 0;
        int loaded = 0;
        for (ResourceAuthorizationCacheEntry entry : entries) {
            if (entry != null) {
                put(entry.key(), entry.decision(), entry.expiresAt());
                loaded++;
            }
        }
        return loaded;
    }

    default ResourceAuthorizationCacheMetrics metrics() {
        return ResourceAuthorizationCacheMetrics.ZERO;
    }
}
