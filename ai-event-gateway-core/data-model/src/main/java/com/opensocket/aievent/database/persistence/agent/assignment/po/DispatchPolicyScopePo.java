package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicyScopePo {
    private String tenantId;
    private String scopeId;
    private String policyCode;
    private String sourceSystem;
    private String taskType;
    private String taskDefinitionId;
    private String riskLevel;
    private String priority;
    private String conditionExpr;
    private boolean active;
    private int priorityOrder;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

}
