package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;

/** Trusted internal context for server-side descriptor resolution. */
public record DescriptorResolutionContext(String correlationId, String requestingModule, Instant requestedAt) {
    public DescriptorResolutionContext {
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId is required");
        if (requestingModule == null || requestingModule.isBlank()) throw new IllegalArgumentException("requestingModule is required");
        correlationId = correlationId.trim(); requestingModule = requestingModule.trim();
        if (requestedAt == null) throw new IllegalArgumentException("requestedAt is required");
    }
}
