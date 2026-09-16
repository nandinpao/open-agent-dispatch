package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.uicapability.contract.UiCapabilityEnvelope;
import java.time.Instant;
import java.util.Optional;

public interface UiCapabilityProjectionCachePort {
    Optional<UiCapabilityEnvelope> get(UiCapabilityProjectionCacheKey key, Instant now);
    void put(UiCapabilityProjectionCacheKey key, UiCapabilityEnvelope envelope);
    void invalidateTenant(String tenantId);
    void invalidatePrincipal(String tenantId, String principalId);
    void invalidateContext(String tenantId, String principalId, String contextId);
}
