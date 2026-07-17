package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicyRequiredRuntimeFeature {
    private String tenantId;
    private String ruleId;
    private String policyCode;
    private String featureCode;
    private String featureName;
    private String trustStatus = "TRUSTED";
    private String conditionExpr;
    private boolean blocking = true;
    private int priority = 100;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
}
