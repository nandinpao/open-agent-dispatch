package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicy {
    private String tenantId;
    private String policyId;
    private String policyCode;
    private String policyName;
    private String description;
    private String ownerTeam;
    private String riskLevel = "MIDDLE";
    private String status = "DRAFT";
    private int version = 1;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<DispatchPolicyScope> scopes = List.of();
    private List<DispatchPolicyRequiredCapability> requiredCapabilities = List.of();
    private List<DispatchPolicyRequiredRuntimeFeature> requiredRuntimeFeatures = List.of();
    private List<DispatchPolicyQualityRule> qualityRules = List.of();
    private List<DispatchPolicyScoringRule> scoringRules = List.of();

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public void setScopes(List<DispatchPolicyScope> scopes) {
        this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public void setRequiredCapabilities(List<DispatchPolicyRequiredCapability> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
    }

    public void setRequiredRuntimeFeatures(List<DispatchPolicyRequiredRuntimeFeature> requiredRuntimeFeatures) {
        this.requiredRuntimeFeatures = requiredRuntimeFeatures == null ? List.of() : List.copyOf(requiredRuntimeFeatures);
    }

    public void setQualityRules(List<DispatchPolicyQualityRule> qualityRules) {
        this.qualityRules = qualityRules == null ? List.of() : List.copyOf(qualityRules);
    }

    public void setScoringRules(List<DispatchPolicyScoringRule> scoringRules) {
        this.scoringRules = scoringRules == null ? List.of() : List.copyOf(scoringRules);
    }
}
