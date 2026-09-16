package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Explicit governed identity correlation between a MANAGED_AGENT capability provider and an OpenDispatch Agent. */
public record ManagedAgentProviderLink(
        String tenantId,
        String providerId,
        String agentId,
        String status,
        String linkSource,
        OffsetDateTime verifiedAt,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
