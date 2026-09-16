package com.opensocket.aievent.core.routing.cutover;

import java.util.List;

import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;

/**
 * Result returned by the single canonical dispatch authority.
 *
 * <p>V38-7A2 also carries blocked candidate evidence so runtime dispatch,
 * simulation, readiness and why-not-dispatch can project the same reason codes
 * instead of re-evaluating eligibility through independent SQL or legacy
 * services.</p>
 */
public record GenericAuthoritativeRoutingResult(
        Status status,
        TaskRequirementEvidence requirement,
        List<AgentCandidateScore> candidates,
        List<BlockedCandidateEvidence> blockedCandidates,
        AgentCandidateScore selected,
        String reasonCode,
        String reason
) {
    public enum Status { SELECTED, NO_CANDIDATE, MANUAL_REVIEW, REQUIREMENT_BLOCKED, ERROR }

    public record BlockedCandidateEvidence(
            String agentId,
            String runtimeStatus,
            List<String> reasonCodes
    ) {
        public BlockedCandidateEvidence {
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }

    public GenericAuthoritativeRoutingResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        blockedCandidates = blockedCandidates == null ? List.of() : List.copyOf(blockedCandidates);
    }

    public boolean hasSelection() { return status == Status.SELECTED && selected != null; }
    public boolean requirementBlocked() { return status == Status.REQUIREMENT_BLOCKED; }
    public boolean noCandidate() { return status == Status.NO_CANDIDATE || status == Status.REQUIREMENT_BLOCKED || status == Status.ERROR; }
}
