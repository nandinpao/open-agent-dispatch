package com.opensocket.aievent.core.routing.selection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.opensocket.aievent.core.routing.AgentCandidateScore;

abstract class AbstractPoolSelectionStrategy implements AgentSelectionStrategy {
    static final String WEIGHT_FORMULA = "WEIGHTED_SCORE: round(baseScore * 0.8 + normalizedMemberWeight * 20)";

    @Override
    public AgentCandidateScore annotate(AgentCandidateScore score, SelectionStrategyContext context) {
        if (score == null || context == null || !context.hasTargetPool()) {
            return score;
        }
        int baseScore = score.score();
        int memberWeight = context.memberWeight(score.agentId());
        int maxMemberWeight = context.maxMemberWeight();
        int effectiveScore = effectiveScore(baseScore, memberWeight, maxMemberWeight);
        Map<String, Object> breakdown = new LinkedHashMap<>(score.scoreBreakdown() == null ? Map.of() : score.scoreBreakdown());
        breakdown.put("targetPoolId", context.targetPoolId());
        breakdown.put("targetPoolCode", context.targetPoolCode() == null ? "" : context.targetPoolCode());
        breakdown.put("poolSelectionStrategy", strategyCode());
        breakdown.put("poolMemberPriority", context.memberPriority(score.agentId()));
        breakdown.put("poolMemberWeight", memberWeight);
        breakdown.put("poolMaxMemberWeight", maxMemberWeight);
        breakdown.put("poolBaseScore", baseScore);
        breakdown.put("poolWeightedEffectiveScore", effectiveScore);
        breakdown.put("poolWeightFormula", WEIGHT_FORMULA);
        breakdown.put("effectiveTaskCount", context.effectiveTaskCount(score));
        return new AgentCandidateScore(
                score.agentId(), score.ownerGatewayNodeId(), score.agentSessionId(), score.siteId(), score.status(),
                effectiveScore, score.matchedCapabilities(), score.missingCapabilities(),
                score.reason() + ", targetPool=" + context.targetPoolId() + ", poolStrategy=" + strategyCode()
                        + ("WEIGHTED_SCORE".equals(strategyCode()) ? ", weightedEffectiveScore=" + effectiveScore : ""),
                immutableNullableMap(breakdown));
    }

    protected int effectiveScore(int baseScore, int memberWeight, int maxMemberWeight) {
        return baseScore;
    }

    protected static Map<String, Object> immutableNullableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
