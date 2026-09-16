package com.opensocket.aievent.core.capability;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Phase 4 protocol-neutral runtime/quality observation used after WHO MAY.
 * It contains no endpoint, credential, Agent Pool, transport or protocol.
 */
public record ProviderEligibilityObservation(
        String tenantId,
        String observationId,
        String bindingId,
        String providerId,
        String observationSource,
        boolean available,
        boolean healthy,
        boolean capacityAvailable,
        BigDecimal qualityScore,
        BigDecimal reliabilityScore,
        Long p95LatencyMs,
        BigDecimal estimatedCost,
        BigDecimal loadPercent,
        BigDecimal localityScore,
        OffsetDateTime observedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt) {}
