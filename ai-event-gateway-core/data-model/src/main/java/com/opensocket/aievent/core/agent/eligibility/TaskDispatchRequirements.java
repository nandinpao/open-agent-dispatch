package com.opensocket.aievent.core.agent.eligibility;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskDispatchRequirements {
    private String taskId;
    private String taskType;
    private String sourceSystem;
    private String tenantId;
    private List<String> requiredProfiles = List.of();
    private List<TaskDispatchRequirementProfile> profiles = List.of();
    private List<String> requiredRuntimeFeatures = List.of();
    private List<String> requiredCapabilities = List.of();
    private List<String> requiredPolicyCodes = List.of();
    private List<String> taskDefinitionIds = List.of();
    private List<String> allowedIssueProviders = List.of();
    private List<String> toolPolicies = List.of();
    private String riskLevelLimit;
    private String requirementSource = "ASSIGNMENT_PROFILE";
    private OffsetDateTime generatedAt;

    public void setRequiredProfiles(List<String> requiredProfiles) {
        this.requiredProfiles = requiredProfiles == null ? List.of() : List.copyOf(requiredProfiles);
    }

    public void setProfiles(List<TaskDispatchRequirementProfile> profiles) {
        this.profiles = profiles == null ? List.of() : List.copyOf(profiles);
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

    public void setTaskDefinitionIds(List<String> taskDefinitionIds) {
        this.taskDefinitionIds = taskDefinitionIds == null ? List.of() : List.copyOf(taskDefinitionIds);
    }

    public void setAllowedIssueProviders(List<String> allowedIssueProviders) {
        this.allowedIssueProviders = allowedIssueProviders == null ? List.of() : List.copyOf(allowedIssueProviders);
    }

    public void setToolPolicies(List<String> toolPolicies) {
        this.toolPolicies = toolPolicies == null ? List.of() : List.copyOf(toolPolicies);
    }
}
