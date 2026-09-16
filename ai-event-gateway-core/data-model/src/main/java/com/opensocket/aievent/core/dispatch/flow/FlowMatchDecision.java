package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A0-R3 authoritative deterministic FlowMatchDecision model.
 *
 * <p>V188 legacy projection rows remain historical evidence. A0-R3 decisions are
 * fixed once per Task and reference an externalized FlowEvaluationSet for full
 * candidate diagnostics.</p>
 */
public record FlowMatchDecision(
        String decisionId,
        String tenantId,
        String taskId,
        Long taskVersion,
        String decisionSource,
        String flowId,
        String flowVersion,
        int evaluatedRuleCount,
        String matchedRuleId,
        Integer matchedRulePriority,
        List<String> evaluatedRules,
        String outputServiceCode,
        List<String> outputCapabilityRequirements,
        MatchResult matchResult,
        String flowEvaluationSetRef,
        String flowEvaluationSetDigest,
        String closestRuleId,
        Integer closestRulePriority,
        String closestRuleFailedCriterion,
        Double closestRuleMatchRatio,
        String decisionAuthorityVersion,
        OffsetDateTime decidedAt,
        String evaluatorVersion) {

    public enum MatchResult {
        MATCHED,
        NO_MATCH,
        AMBIGUOUS
    }
}
