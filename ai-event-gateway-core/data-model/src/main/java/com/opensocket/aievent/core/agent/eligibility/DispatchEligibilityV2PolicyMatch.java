package com.opensocket.aievent.core.agent.eligibility;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchEligibilityV2PolicyMatch {
    private String policyCode;
    private String policyName;
    private String policyStatus;
    private List<String> matchedScopes = List.of();
    private List<String> requiredCapabilities = List.of();
    private List<String> requiredRuntimeFeatures = List.of();
    private List<String> qualityRules = List.of();
    private List<String> scoringRules = List.of();

    public void setMatchedScopes(List<String> matchedScopes) {
        this.matchedScopes = matchedScopes == null ? List.of() : List.copyOf(matchedScopes);
    }
    public void setRequiredCapabilities(List<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
    }
    public void setRequiredRuntimeFeatures(List<String> requiredRuntimeFeatures) {
        this.requiredRuntimeFeatures = requiredRuntimeFeatures == null ? List.of() : List.copyOf(requiredRuntimeFeatures);
    }
    public void setQualityRules(List<String> qualityRules) {
        this.qualityRules = qualityRules == null ? List.of() : List.copyOf(qualityRules);
    }
    public void setScoringRules(List<String> scoringRules) {
        this.scoringRules = scoringRules == null ? List.of() : List.copyOf(scoringRules);
    }
}
