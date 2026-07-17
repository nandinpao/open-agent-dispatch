package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicyRequiredRuntimeFeaturePo {
    private String tenantId;
    private String ruleId;
    private String policyCode;
    private String featureCode;
    private String featureName;
    private String trustStatus;
    private String conditionExpr;
    private boolean blocking;
    private int priority;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

}
