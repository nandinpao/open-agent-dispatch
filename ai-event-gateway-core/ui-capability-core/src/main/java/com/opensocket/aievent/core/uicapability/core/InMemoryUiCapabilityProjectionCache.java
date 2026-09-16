package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.uicapability.contract.UiCapabilityEnvelope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Bounded node-local optimization. Exact epoch/version keys prevent authority widening. */
public final class InMemoryUiCapabilityProjectionCache implements UiCapabilityProjectionCachePort {
    private final int maximumEntries;
    private final Map<UiCapabilityProjectionCacheKey, UiCapabilityEnvelope> entries;

    public InMemoryUiCapabilityProjectionCache(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
        this.maximumEntries = maximumEntries;
        this.entries = new LinkedHashMap<>(128, .75f, true);
    }

    @Override public synchronized Optional<UiCapabilityEnvelope> get(UiCapabilityProjectionCacheKey key, Instant now) {
        UiCapabilityEnvelope value = entries.get(key);
        if (value == null) return Optional.empty();
        if (!now.isBefore(value.expiresAt())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(value);
    }

    @Override public synchronized void put(UiCapabilityProjectionCacheKey key, UiCapabilityEnvelope envelope) {
        entries.put(key, envelope);
        while (entries.size() > maximumEntries) {
            UiCapabilityProjectionCacheKey eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
    }

    @Override public synchronized void invalidateTenant(String tenantId) {
        entries.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }
    @Override public synchronized void invalidatePrincipal(String tenantId, String principalId) {
        entries.keySet().removeIf(key -> key.tenantId().equals(tenantId) && key.principalId().equals(principalId));
    }
    @Override public synchronized void invalidateContext(String tenantId, String principalId, String contextId) {
        entries.keySet().removeIf(key -> key.tenantId().equals(tenantId)
                && key.principalId().equals(principalId) && key.contextId().equals(contextId));
    }
}
