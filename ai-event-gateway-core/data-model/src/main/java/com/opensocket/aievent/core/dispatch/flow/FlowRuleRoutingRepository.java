package com.opensocket.aievent.core.dispatch.flow;

import java.util.List;
import java.util.Optional;

/** Repository port for authoritative deterministic Flow Rule matching. */
public interface FlowRuleRoutingRepository {
    /**
     * Legacy-compatible single-result access. Implementations must never hide A0-R3 ambiguity;
     * ambiguous or no-match evaluations return Optional.empty().
     */
    Optional<FlowRuleRuntimeMatch> findBestMatch(FlowRuleRuntimeQuery query);

    /**
     * A0-R3 authoritative evaluation. Default keeps existing test doubles/source adapters compatible;
     * the JDBC adapter overrides this with full 0/1/many deterministic evaluation.
     */
    default FlowRuleEvaluation evaluate(FlowRuleRuntimeQuery query) {
        Optional<FlowRuleRuntimeMatch> match = findBestMatch(query);
        if (match.isEmpty()) {
            return new FlowRuleEvaluation(FlowMatchDecision.MatchResult.NO_MATCH, List.of(), List.of(), List.of(), null, "LEGACY_ADAPTER_V1");
        }
        FlowRuleRuntimeMatch rule = match.get();
        int priority = rule.getPriority() == null ? 100 : rule.getPriority();
        FlowRuleEvaluationCandidate candidate = new FlowRuleEvaluationCandidate(rule, priority, true, null, List.of(), 1, 1, 1d);
        return new FlowRuleEvaluation(FlowMatchDecision.MatchResult.MATCHED, List.of(candidate), List.of(candidate), List.of(), priority, "LEGACY_ADAPTER_V1");
    }

    /**
     * C7 simulation-only evaluation. Production adapters must keep {@link #evaluate(FlowRuleRuntimeQuery)}
     * ACTIVE/ENABLED-only. A JDBC adapter may override this method to include the explicitly selected
     * Draft Flow while preserving the same deterministic rule-matching algorithm.
     */
    default FlowRuleEvaluation evaluateForSimulation(FlowRuleRuntimeQuery query) {
        return evaluate(query);
    }

    /**
     * Persist canonical FlowMatchDecision after Task materialization. Default is intentionally a
     * no-op for in-memory/characterization adapters; production JDBC overrides it.
     */
    default void recordAuthoritativeDecision(String taskId, long taskVersion, FlowRuleRuntimeQuery query, FlowRuleEvaluation evaluation) {
        // no-op compatibility adapter
    }
}
