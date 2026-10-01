package com.opensocket.aievent.core.kernel.configuration.inventory;

import java.time.OffsetDateTime;

/** Append-only evidence for configuration-inventory governance decisions. */
public record ConfigurationInventoryGovernanceEvent(
    long eventId,
    String configurationKey,
    String eventType,
    String fromStatus,
    String toStatus,
    String actor,
    String reason,
    String sourceObservationHash,
    String detailJson,
    OffsetDateTime createdAt
) {}
