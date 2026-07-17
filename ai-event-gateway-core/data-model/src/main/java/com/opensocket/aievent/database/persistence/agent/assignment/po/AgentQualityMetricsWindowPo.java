package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentQualityMetricsWindowPo {
    private String tenantId;
    private String metricId;

    private String agentId;
    private String runtimeId;
    private String bindingId;
    private String supplyProfileId;
    private String profileCode;
    private String metricWindow;
    private OffsetDateTime windowStart;
    private OffsetDateTime windowEnd;
    private BigDecimal successRate;
    private BigDecimal failureRate;
    private BigDecimal timeoutRate;
    private BigDecimal slaBreachRate;
    private long avgAckLatencyMs;
    private long avgCompletionLatencyMs;
    private int recentFailureCount;
    private BigDecimal manualRating;
    private String qualityGrade;
    private BigDecimal riskPenalty;
    private BigDecimal score;
    private int sampleSize;
    private OffsetDateTime calculatedAt;
    private String source;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
