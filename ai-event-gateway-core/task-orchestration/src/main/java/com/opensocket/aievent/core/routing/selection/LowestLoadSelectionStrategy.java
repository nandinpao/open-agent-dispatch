package com.opensocket.aievent.core.routing.selection;

import java.util.Comparator;

import com.opensocket.aievent.core.routing.AgentCandidateScore;

/** Selects the candidate with the lowest effective task count, then highest score. */
public final class LowestLoadSelectionStrategy extends AbstractPoolSelectionStrategy {
    @Override
    public String strategyCode() {
        return "LOWEST_LOAD";
    }

    @Override
    public Comparator<AgentCandidateScore> comparator(SelectionStrategyContext context) {
        Comparator<AgentCandidateScore> scoreDescending = Comparator.comparingInt(AgentCandidateScore::score).reversed();
        Comparator<AgentCandidateScore> priorityAscending = Comparator.comparingInt(score -> context.memberPriority(score.agentId()));
        Comparator<AgentCandidateScore> weightDescending = (left, right) -> Integer.compare(context.memberWeight(right.agentId()), context.memberWeight(left.agentId()));
        return Comparator.<AgentCandidateScore>comparingInt(score -> context.intBreakdown(score, "effectiveTaskCount"))
                .thenComparing(scoreDescending)
                .thenComparing(priorityAscending)
                .thenComparing(weightDescending);
    }
}
