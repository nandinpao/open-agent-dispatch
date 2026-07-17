package com.opensocket.aievent.core.agent.assignment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplyProfileQualitySnapshot {
    private String tenantId;
    private String metricId;
    private String snapshotId;
    private String agentId;
    private String runtimeId;
    private String bindingId;
    private String supplyProfileId;
    private String profileCode;
    private String metricWindow = "24h";
    private OffsetDateTime windowStart;
    private OffsetDateTime windowEnd;
    private BigDecimal successRate = BigDecimal.ZERO;
    private BigDecimal failureRate = BigDecimal.ZERO;
    private BigDecimal timeoutRate = BigDecimal.ZERO;
    private BigDecimal slaBreachRate = BigDecimal.ZERO;
    private long avgAckLatencyMs;
    private long avgCompletionLatencyMs;
    private int recentFailureCount;
    private BigDecimal manualRating;
    private String qualityGrade = "UNKNOWN";
    private BigDecimal riskPenalty = BigDecimal.ZERO;
    private BigDecimal score = BigDecimal.ZERO;
    private int sampleSize;
    private OffsetDateTime calculatedAt;
    private String source;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
