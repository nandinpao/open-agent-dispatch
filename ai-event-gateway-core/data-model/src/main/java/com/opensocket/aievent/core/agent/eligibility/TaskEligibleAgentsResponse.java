package com.opensocket.aievent.core.agent.eligibility;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskEligibleAgentsResponse {
    private String taskId;
    private TaskDispatchRequirements requirements;
    private List<EligibleAgentCandidate> eligibleAgents = List.of();
    private List<EligibleAgentCandidate> blockedAgents = List.of();
    private OffsetDateTime generatedAt;

    public void setEligibleAgents(List<EligibleAgentCandidate> eligibleAgents) {
        this.eligibleAgents = eligibleAgents == null ? List.of() : List.copyOf(eligibleAgents);
    }

    public void setBlockedAgents(List<EligibleAgentCandidate> blockedAgents) {
        this.blockedAgents = blockedAgents == null ? List.of() : List.copyOf(blockedAgents);
    }
}
