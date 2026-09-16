package com.opensocket.aievent.core.dispatch.flow;

import java.util.List;

/**
 * Deterministic evaluation evidence for one persisted Flow Rule.
 *
 * <p>The authoritative match boolean is exact/wildcard only. matchRatio is diagnostic
 * evidence for NO_MATCH explainability and MUST NOT be used to select a winner.</p>
 */
public record FlowRuleEvaluationCandidate(
        FlowRuleRuntimeMatch rule,
        int priority,
        boolean matched,
        String failedCriterion,
        List<String> failedCriteria,
        int matchedCriteriaCount,
        int totalCriteriaCount,
        double matchRatio) {

    public FlowRuleEvaluationCandidate {
        failedCriteria = failedCriteria == null ? List.of() : List.copyOf(failedCriteria);
        if (totalCriteriaCount < 0) totalCriteriaCount = 0;
        if (matchedCriteriaCount < 0) matchedCriteriaCount = 0;
        if (matchRatio < 0d) matchRatio = 0d;
        if (matchRatio > 1d) matchRatio = 1d;
    }
}
