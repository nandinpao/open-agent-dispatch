package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssignmentProfilePolicyBinding {
    private String tenantId;
    private String bindingId;
    private String profileCode;
    private String policyCode;
    private String policyName;
    private String bindingMode = "REQUIRED";
    private boolean required = true;
    private boolean active = true;
    private int priority = 100;
    private String conditionExpr;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
