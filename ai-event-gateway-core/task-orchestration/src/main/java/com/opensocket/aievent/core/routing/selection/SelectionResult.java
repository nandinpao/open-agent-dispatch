package com.opensocket.aievent.core.routing.selection;

import java.util.List;

import com.opensocket.aievent.core.routing.AgentCandidateScore;

/** Result of applying the configured Agent Pool selection strategy. */
public record SelectionResult(
        String strategy,
        boolean manualOnly,
        List<AgentCandidateScore> candidates) {

    public SelectionResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
