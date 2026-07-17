package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentAssignmentProfilePolicyBindingPo {
    private String tenantId;
    private String bindingId;
    private String profileCode;
    private String policyCode;
    private String policyName;
    private String bindingMode;
    private boolean required;
    private boolean active;
    private int priority;
    private String conditionExpr;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
