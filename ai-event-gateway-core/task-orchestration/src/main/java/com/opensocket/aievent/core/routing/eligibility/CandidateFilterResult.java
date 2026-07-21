package com.opensocket.aievent.core.routing.eligibility;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingMember;

/**
 * Immutable result of runtime eligibility filtering for routing candidates.
 *
 * <p>Phase 3-3 extracts this value object from {@code RoutingDecisionService}
 * without changing routing behaviour or diagnostic text. It intentionally
 * preserves the same evidence fields used by task detail and routing logs.</p>
 */
public record CandidateFilterResult(
        List<AgentSnapshot> included,
        Set<String> reservationExcluded,
        Set<String> poisonExcluded,
        String targetPoolId,
        String targetPoolCode,
        String selectionStrategy,
        Map<String, AgentPoolRoutingMember> membersByAgentId,
        String poolBlockerCode) {

    public static CandidateFilterResult empty(Set<String> excluded) {
        return new CandidateFilterResult(List.of(), excluded == null ? Set.of() : excluded, Set.of(), null, null, null, Map.of(), null);
    }

    public static CandidateFilterResult blocked(String poolId, String poolCode, String blockerCode, Set<String> excluded) {
        return new CandidateFilterResult(List.of(), excluded == null ? Set.of() : excluded, Set.of(), poolId, poolCode,
                "LOWEST_LOAD", Map.of(), blockerCode);
    }

    public CandidateFilterResult withPool(String poolId, String poolCode, String strategy, Map<String, AgentPoolRoutingMember> members, String blockerCode) {
        return new CandidateFilterResult(included, reservationExcluded, poisonExcluded, poolId, poolCode,
                strategy == null || strategy.isBlank() ? "LOWEST_LOAD" : strategy, members == null ? Map.of() : Map.copyOf(members), blockerCode);
    }

    public int memberCount() {
        return membersByAgentId == null ? 0 : membersByAgentId.size();
    }

    public int eligibleAgentCount() {
        return included == null ? 0 : included.size();
    }

    public String diagnostics() {
        StringBuilder builder = new StringBuilder();
        if (targetPoolId != null && !targetPoolId.isBlank()) {
            builder.append("; pool={targetPoolId=").append(targetPoolId)
                    .append(", targetPoolCode=").append(targetPoolCode)
                    .append(", selectionStrategy=").append(selectionStrategy)
                    .append(", memberCount=").append(memberCount())
                    .append(", eligibleAgentCount=").append(eligibleAgentCount());
            if (poolBlockerCode != null && !poolBlockerCode.isBlank()) {
                builder.append(", blocker=").append(poolBlockerCode);
            }
            builder.append("}");
        }
        if (reservationExcluded != null && !reservationExcluded.isEmpty()) {
            builder.append("; excludedAfterReservationRace=").append(reservationExcluded);
        }
        if (poisonExcluded != null && !poisonExcluded.isEmpty()) {
            builder.append("; poisonAgentExcluded=").append(poisonExcluded);
        }
        return builder.toString();
    }
}
