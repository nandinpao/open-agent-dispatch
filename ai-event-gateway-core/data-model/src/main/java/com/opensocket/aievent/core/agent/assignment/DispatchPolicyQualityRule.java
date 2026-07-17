package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicyQualityRule {
    private String tenantId;
    private String ruleId;
    private String policyCode;
    private String metricName;
    private String operator = ">=";
    private String thresholdValue;
    private String metricWindow = "24h";
    private boolean blocking = true;
    private int scoreWeight;
    private String conditionExpr;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
}
