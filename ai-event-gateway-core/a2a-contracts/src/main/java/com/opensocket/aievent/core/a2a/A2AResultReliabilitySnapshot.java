package com.opensocket.aievent.core.a2a;

import java.util.List;

/** Provider-neutral read model for Result Acceptance and parent aggregation operations. */
public record A2AResultReliabilitySnapshot(
        String taskId,
        List<ResultView> results,
        List<AggregationView> aggregations) {

    public A2AResultReliabilitySnapshot {
        results = results == null ? List.of() : List.copyOf(results);
        aggregations = aggregations == null ? List.of() : List.copyOf(aggregations);
    }

    public record ResultView(
            A2AResult result,
            A2AResultProcessing processing,
            List<A2AResultAttempt> attempts,
            List<A2AResultEvidence> evidence,
            List<A2AResultQuarantine> quarantine) {
        public ResultView {
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            quarantine = quarantine == null ? List.of() : List.copyOf(quarantine);
        }
    }

    public record AggregationView(
            String parentTaskId,
            A2AParentAggregation aggregation,
            List<A2AAggregationEvidence> evidence) {
        public AggregationView {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }
}
