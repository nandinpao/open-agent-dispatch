package com.opensocket.aievent.core.routing.selection;

import java.util.Comparator;

import com.opensocket.aievent.core.routing.AgentCandidateScore;

/**
 * Selects and annotates Agent Pool candidates without loading data or creating assignments.
 *
 * Implementations must be deterministic for the same candidate list and selection context.
 */
public interface AgentSelectionStrategy {
    String strategyCode();

    default boolean manualOnly() {
        return false;
    }

    AgentCandidateScore annotate(AgentCandidateScore score, SelectionStrategyContext context);

    Comparator<AgentCandidateScore> comparator(SelectionStrategyContext context);
}
