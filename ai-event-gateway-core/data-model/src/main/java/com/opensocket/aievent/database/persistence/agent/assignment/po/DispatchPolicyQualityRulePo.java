package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicyQualityRulePo {
    private String tenantId;
    private String ruleId;
    private String policyCode;
    private String metricName;
    private String operator;
    private String thresholdValue;
    private String metricWindow;
    private boolean blocking;
    private int scoreWeight;
    private String conditionExpr;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

}
