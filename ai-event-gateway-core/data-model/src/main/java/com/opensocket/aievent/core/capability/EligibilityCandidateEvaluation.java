package com.opensocket.aievent.core.capability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** A0-R6 immutable candidate evaluation. Authorization is a hard gate and is never a score. */
public record EligibilityCandidateEvaluation(
        String evaluationId,String bindingId,String providerId,String providerType,String bindingClass,
        String agentPoolId,String observationId,String result,List<String> reasonCodes,
        Map<String,BigDecimal> scoreDimensions,BigDecimal normalizedWeightedScore) {
    public EligibilityCandidateEvaluation {
        reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes);
        scoreDimensions=scoreDimensions==null?Map.of():Map.copyOf(scoreDimensions);
    }
}
