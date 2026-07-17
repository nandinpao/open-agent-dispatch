package com.opensocket.aievent.core.agent.eligibility;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchEligibilityV2Candidate {
    private String agentId;
    private String runtimeId;
    private String bindingId;
    private String supplyProfileId;
    private String supplyProfileCode;
    private String serviceRole;
    private String serviceLevel;
    private String qualityGrade;
    private boolean eligible;
    private String dispatchStatus;
    private int score;
    private List<String> matchedPolicyCodes = List.of();
    private List<String> matchedCapabilities = List.of();
    private List<String> matchedRuntimeFeatures = List.of();
    private List<DispatchEligibilityV2BlockingReason> blockingReasons = List.of();
    private List<DispatchEligibilityV2ScoreBreakdown> scoreBreakdown = List.of();

    public void setMatchedPolicyCodes(List<String> matchedPolicyCodes) {
        this.matchedPolicyCodes = matchedPolicyCodes == null ? List.of() : List.copyOf(matchedPolicyCodes);
    }
    public void setMatchedCapabilities(List<String> matchedCapabilities) {
        this.matchedCapabilities = matchedCapabilities == null ? List.of() : List.copyOf(matchedCapabilities);
    }
    public void setMatchedRuntimeFeatures(List<String> matchedRuntimeFeatures) {
        this.matchedRuntimeFeatures = matchedRuntimeFeatures == null ? List.of() : List.copyOf(matchedRuntimeFeatures);
    }
    public void setBlockingReasons(List<DispatchEligibilityV2BlockingReason> blockingReasons) {
        this.blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
    }
    public void setScoreBreakdown(List<DispatchEligibilityV2ScoreBreakdown> scoreBreakdown) {
        this.scoreBreakdown = scoreBreakdown == null ? List.of() : List.copyOf(scoreBreakdown);
    }
}
