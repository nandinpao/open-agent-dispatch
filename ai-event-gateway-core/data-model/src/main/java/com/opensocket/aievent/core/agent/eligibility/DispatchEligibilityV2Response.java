package com.opensocket.aievent.core.agent.eligibility;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchEligibilityV2Response {
    private String taskId;
    private String tenantId;
    private String sourceSystem;
    private String taskType;
    private String engineMode = "SHADOW";
    private String requirementSource = "P3H_POLICY_SUPPLY_EVALUATION";
    private List<DispatchEligibilityV2PolicyMatch> applicablePolicies = List.of();
    private List<DispatchEligibilityV2Candidate> eligibleCandidates = List.of();
    private List<DispatchEligibilityV2Candidate> blockedCandidates = List.of();
    private List<DispatchEligibilityV2BlockingReason> globalBlockingReasons = List.of();
    private OffsetDateTime generatedAt;

    public void setApplicablePolicies(List<DispatchEligibilityV2PolicyMatch> applicablePolicies) {
        this.applicablePolicies = applicablePolicies == null ? List.of() : List.copyOf(applicablePolicies);
    }
    public void setEligibleCandidates(List<DispatchEligibilityV2Candidate> eligibleCandidates) {
        this.eligibleCandidates = eligibleCandidates == null ? List.of() : List.copyOf(eligibleCandidates);
    }
    public void setBlockedCandidates(List<DispatchEligibilityV2Candidate> blockedCandidates) {
        this.blockedCandidates = blockedCandidates == null ? List.of() : List.copyOf(blockedCandidates);
    }
    public void setGlobalBlockingReasons(List<DispatchEligibilityV2BlockingReason> globalBlockingReasons) {
        this.globalBlockingReasons = globalBlockingReasons == null ? List.of() : List.copyOf(globalBlockingReasons);
    }
}
