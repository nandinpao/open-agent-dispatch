package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** A0-R6 RoutingDecision. Authority ends at Binding/Pool; runtime target selection is separate. */
public record R6RoutingDecision(
        String decisionId,String tenantId,String result,String planId,int planRevision,String stepId,String envelopeId,
        String eligibilityDecisionId,String routingFeatureSnapshotRef,String capabilityCode,String operation,
        String routingProfileId,int routingProfileVersion,String selectedBindingId,String selectedProviderId,
        String selectedAgentPoolId,List<String> reasonCodes,String authorityMode,OffsetDateTime decidedAt) {
    public R6RoutingDecision { reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes); }
}
