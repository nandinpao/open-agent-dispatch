package com.opensocket.aievent.core.routing.cutover;

import java.util.List;

import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;

public record GenericAuthoritativeRoutingResult(
        Status status,
        TaskRequirementEvidence requirement,
        List<AgentCandidateScore> candidates,
        AgentCandidateScore selected,
        String reasonCode,
        String reason
) {
    public enum Status { SELECTED, NO_CANDIDATE, MANUAL_REVIEW, REQUIREMENT_BLOCKED, ERROR }
    public boolean hasSelection() { return status == Status.SELECTED && selected != null; }
    public boolean requirementBlocked() { return status == Status.REQUIREMENT_BLOCKED; }
    public boolean noCandidate() { return status == Status.NO_CANDIDATE || status == Status.REQUIREMENT_BLOCKED || status == Status.ERROR; }
}
