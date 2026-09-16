package com.opensocket.aievent.core.dispatch.flow;

import java.util.List;
import java.util.Optional;

/**
 * A0-R3 authoritative deterministic Flow Rule evaluation.
 *
 * <p>Winner semantics are fixed by MRS v5.2.1: evaluate all deterministic candidates,
 * then inspect the minimum-priority matched bucket. There is no updatedAt/ruleId
 * winner tie-break.</p>
 */
public record FlowRuleEvaluation(
        FlowMatchDecision.MatchResult matchResult,
        List<FlowRuleEvaluationCandidate> evaluatedRules,
        List<FlowRuleEvaluationCandidate> minimumPriorityMatches,
        List<FlowRuleEvaluationCandidate> closestRules,
        Integer minimumMatchedPriority,
        String evaluatorVersion) {

    public FlowRuleEvaluation {
        evaluatedRules = evaluatedRules == null ? List.of() : List.copyOf(evaluatedRules);
        minimumPriorityMatches = minimumPriorityMatches == null ? List.of() : List.copyOf(minimumPriorityMatches);
        closestRules = closestRules == null ? List.of() : List.copyOf(closestRules);
    }

    public Optional<FlowRuleRuntimeMatch> selectedMatch() {
        if (matchResult != FlowMatchDecision.MatchResult.MATCHED || minimumPriorityMatches.size() != 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(minimumPriorityMatches.get(0).rule());
    }

    public boolean isAmbiguous() {
        return matchResult == FlowMatchDecision.MatchResult.AMBIGUOUS;
    }

    public boolean isNoMatch() {
        return matchResult == FlowMatchDecision.MatchResult.NO_MATCH;
    }
}
