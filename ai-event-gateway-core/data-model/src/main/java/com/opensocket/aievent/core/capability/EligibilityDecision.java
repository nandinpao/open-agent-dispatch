package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** A0-R6 WHO CAN NOW decision constrained by one active R5 BindingAuthorizationEnvelope. */
public record EligibilityDecision(
        String decisionId,String tenantId,String planId,int planRevision,String stepId,String taskId,String envelopeId,
        String capabilityCode,int capabilityVersion,String operation,String routingProfileId,int routingProfileVersion,
        int candidateCount,int eligibleCount,int excludedCount,String result,Map<String,Integer> exclusionSummary,
        String candidateEvaluationSetDigest,String policySnapshotRef,OffsetDateTime evaluatedAt) {
    public EligibilityDecision { exclusionSummary=exclusionSummary==null?Map.of():Map.copyOf(exclusionSummary); }
}
