package com.opensocket.aievent.core.routing.selection;

import java.util.Comparator;

import com.opensocket.aievent.core.routing.AgentCandidateScore;

/** Marks a pool as manual-only; automatic scoring and assignment must not run. */
public final class ManualOnlySelectionStrategy extends AbstractPoolSelectionStrategy {
    @Override
    public String strategyCode() {
        return "MANUAL_ONLY";
    }

    @Override
    public boolean manualOnly() {
        return true;
    }

    @Override
    public Comparator<AgentCandidateScore> comparator(SelectionStrategyContext context) {
        return Comparator.comparingInt(AgentCandidateScore::score).reversed();
    }
}
