package com.opensocket.aievent.core.agent.eligibility;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskDispatchRequirementProfile {
    private String profileCode;
    private String profileName;
    private String agentType;
    private List<String> allowedTaskTypes = List.of();
    private List<String> allowedIssueProviders = List.of();
    private List<String> requiredRuntimeFeatures = List.of();
    private List<String> requiredCapabilities = List.of();
    private List<String> requiredPolicyCodes = List.of();
    private String taskDefinitionId;
    private String sourceSystem;
    private String taskType;
    private String toolPolicy;
    private String riskLevelLimit;
    private boolean requiresCertification;
    private boolean requiresHumanApproval;

    public void setAllowedTaskTypes(List<String> allowedTaskTypes) {
        this.allowedTaskTypes = allowedTaskTypes == null ? List.of() : List.copyOf(allowedTaskTypes);
    }

    public void setAllowedIssueProviders(List<String> allowedIssueProviders) {
        this.allowedIssueProviders = allowedIssueProviders == null ? List.of() : List.copyOf(allowedIssueProviders);
    }

    public void setRequiredRuntimeFeatures(List<String> requiredRuntimeFeatures) {
        this.requiredRuntimeFeatures = requiredRuntimeFeatures == null ? List.of() : List.copyOf(requiredRuntimeFeatures);
    }

    public void setRequiredCapabilities(List<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
    }

    public void setRequiredPolicyCodes(List<String> requiredPolicyCodes) {
        this.requiredPolicyCodes = requiredPolicyCodes == null ? List.of() : List.copyOf(requiredPolicyCodes);
    }
}
