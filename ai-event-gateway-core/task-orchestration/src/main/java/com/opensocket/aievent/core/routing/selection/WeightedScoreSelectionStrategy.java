package com.opensocket.aievent.core.routing.selection;

import java.util.Comparator;

import com.opensocket.aievent.core.routing.AgentCandidateScore;

/** Applies the Phase 1-1 pool member weight formula before sorting by effective score. */
public final class WeightedScoreSelectionStrategy extends AbstractPoolSelectionStrategy {
    @Override
    public String strategyCode() {
        return "WEIGHTED_SCORE";
    }

    @Override
    protected int effectiveScore(int baseScore, int memberWeight, int maxMemberWeight) {
        int safeBaseScore = Math.max(0, Math.min(100, baseScore));
        int safeMemberWeight = Math.max(1, memberWeight);
        int safeMaxWeight = Math.max(1, maxMemberWeight);
        double normalizedMemberWeight = Math.min(1.0d, safeMemberWeight / (double) safeMaxWeight);
        return (int) Math.round((safeBaseScore * 0.8d) + (normalizedMemberWeight * 20.0d));
    }

    @Override
    public Comparator<AgentCandidateScore> comparator(SelectionStrategyContext context) {
        Comparator<AgentCandidateScore> scoreDescending = Comparator.comparingInt(AgentCandidateScore::score).reversed();
        Comparator<AgentCandidateScore> weightDescending = (left, right) -> Integer.compare(context.memberWeight(right.agentId()), context.memberWeight(left.agentId()));
        Comparator<AgentCandidateScore> priorityAscending = Comparator.comparingInt(score -> context.memberPriority(score.agentId()));
        return scoreDescending
                .thenComparing(weightDescending)
                .thenComparing(priorityAscending);
    }
}
