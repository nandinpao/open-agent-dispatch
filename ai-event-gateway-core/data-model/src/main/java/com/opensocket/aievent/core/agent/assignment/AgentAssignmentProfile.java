package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssignmentProfile {
    private String tenantId;
    private String profileId;
    private String profileCode;
    private String profileName;
    private String agentType = "ANY";
    private String taskDefinitionId;
    private String sourceSystem;
    private String taskType;
    private String description;
    private List<String> allowedTaskTypes = List.of();
    private List<String> allowedIssueProviders = List.of();
    private List<String> requiredRuntimeFeatures = List.of();
    private String toolPolicy;
    private String riskLevelLimit = "MIDDLE";
    private boolean requiresCertification;
    private boolean requiresHumanApproval = true;
    private boolean active = true;
    private int policyVersion = 1;
    private OffsetDateTime effectiveAt;
    private OffsetDateTime expiresAt;
    private int renewalRequiredBeforeDays = 14;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private List<AgentAssignmentProfilePolicyBinding> policyBindings = List.of();
    private List<AssignmentProfileCapabilityBinding> capabilityBindings = List.of();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setAllowedTaskTypes(List<String> allowedTaskTypes) {
        this.allowedTaskTypes = allowedTaskTypes == null ? List.of() : List.copyOf(allowedTaskTypes);
    }

    public void setAllowedIssueProviders(List<String> allowedIssueProviders) {
        this.allowedIssueProviders = allowedIssueProviders == null ? List.of() : List.copyOf(allowedIssueProviders);
    }

    public void setRequiredRuntimeFeatures(List<String> requiredRuntimeFeatures) {
        this.requiredRuntimeFeatures = requiredRuntimeFeatures == null ? List.of() : List.copyOf(requiredRuntimeFeatures);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public void setPolicyBindings(List<AgentAssignmentProfilePolicyBinding> policyBindings) {
        this.policyBindings = policyBindings == null ? List.of() : List.copyOf(policyBindings);
    }

    public void setCapabilityBindings(List<AssignmentProfileCapabilityBinding> capabilityBindings) {
        this.capabilityBindings = capabilityBindings == null ? List.of() : List.copyOf(capabilityBindings);
    }
}
