package com.opensocket.aievent.core.a2a;

import java.util.List;

public record A2ACancellationReliabilitySnapshot(
        A2ACancellationRecord cancellation,
        List<A2ACancellationEvidence> evidence,
        List<A2AResultQuarantine> lateResults) {
    public A2ACancellationReliabilitySnapshot {
        evidence=evidence==null?List.of():List.copyOf(evidence);
        lateResults=lateResults==null?List.of():List.copyOf(lateResults);
    }
}
