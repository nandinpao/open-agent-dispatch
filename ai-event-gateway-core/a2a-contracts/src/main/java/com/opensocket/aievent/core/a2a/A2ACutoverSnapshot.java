package com.opensocket.aievent.core.a2a;

import java.util.List;

public record A2ACutoverSnapshot(
        A2ACutoverState state,
        List<A2ACutoverEvidence> evidence,
        List<String> blockers,
        boolean phase2ReleaseEligible) {
    public A2ACutoverSnapshot {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
