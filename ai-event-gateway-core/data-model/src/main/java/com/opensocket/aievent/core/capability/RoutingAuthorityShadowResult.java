package com.opensocket.aievent.core.capability;

import java.util.List;

/** One explainable A0-R6 shadow evaluation. */
public record RoutingAuthorityShadowResult(
        EligibilityDecision eligibilityDecision,List<EligibilityCandidateEvaluation> candidateEvaluations,
        RoutingFeatureSnapshot routingFeatureSnapshot,R6RoutingDecision routingDecision,
        ExecutionAssignmentShadow executionAssignment,FlowRoutingMigrationState flowMigrationState,
        String legacyAssignmentId,String legacyAgentId,String legacyPoolId,boolean executorEquivalent) {
    public RoutingAuthorityShadowResult { candidateEvaluations=candidateEvaluations==null?List.of():List.copyOf(candidateEvaluations); }
}
