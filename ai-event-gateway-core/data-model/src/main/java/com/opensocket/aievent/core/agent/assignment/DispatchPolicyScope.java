package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicyScope {
    private String tenantId;
    private String scopeId;
    private String policyCode;
    private String sourceSystem;
    private String taskType;
    private String taskDefinitionId;
    private String riskLevel;
    private String priority;
    private String conditionExpr;
    private boolean active = true;
    private int priorityOrder = 100;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
}
