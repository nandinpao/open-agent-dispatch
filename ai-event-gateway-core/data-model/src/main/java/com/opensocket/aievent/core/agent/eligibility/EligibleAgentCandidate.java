package com.opensocket.aievent.core.agent.eligibility;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EligibleAgentCandidate {
    private String agentId;
    private String agentType;
    private String profileCode;
    private List<String> matchedProfiles = List.of();
    private int score;
    private boolean eligible;
    private String dispatchStatus;
    private String reason;
    private List<DispatchEligibilityCheck> checks = List.of();

    public void setMatchedProfiles(List<String> matchedProfiles) {
        this.matchedProfiles = matchedProfiles == null ? List.of() : List.copyOf(matchedProfiles);
    }

    public void setChecks(List<DispatchEligibilityCheck> checks) {
        this.checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
