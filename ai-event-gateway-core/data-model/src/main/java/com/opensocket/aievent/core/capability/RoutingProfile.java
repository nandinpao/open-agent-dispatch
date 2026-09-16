package com.opensocket.aievent.core.capability;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** Phase 4 WHO SHOULD ranking policy. It cannot grant authorization or select transport. */
public record RoutingProfile(
        String tenantId,
        String profileId,
        String displayName,
        String description,
        String profileType,
        String status,
        Map<String, Integer> weights,
        BigDecimal minQualityScore,
        BigDecimal minReliabilityScore,
        Long maxP95LatencyMs,
        BigDecimal maxEstimatedCost,
        BigDecimal maxLoadPercent,
        Integer maxObservationAgeSeconds,
        Integer version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public RoutingProfile {
        weights = weights == null ? Map.of() : Map.copyOf(weights);
    }
}
