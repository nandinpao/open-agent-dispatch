package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicyScoringRulePo {
    private String tenantId;
    private String ruleId;
    private String policyCode;
    private String factorName;
    private int weight;
    private String direction;
    private String conditionExpr;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

}
