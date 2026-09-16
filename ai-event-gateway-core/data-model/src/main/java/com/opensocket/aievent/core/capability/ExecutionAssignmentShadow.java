package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** R6 assignment evidence only. No ExecutionLease/fencing/DispatchIntent or side-effect authority exists here. */
public record ExecutionAssignmentShadow(
        String assignmentId,String tenantId,String taskId,String planId,int planRevision,String stepId,
        String routingDecisionId,String bindingId,String providerId,String agentPoolId,String selectedAgentId,
        String selectedSessionId,String selectedPeerInterfaceId,String selectedMcpServerId,String selectedAdapterId,
        String assignmentReason,Map<String,Object> runtimeLoadSnapshot,int attemptNumber,String previousAssignmentId,
        String authorityMode,boolean sideEffectAllowed,OffsetDateTime assignedAt,OffsetDateTime releasedAt,String outcome) {
    public ExecutionAssignmentShadow { runtimeLoadSnapshot=runtimeLoadSnapshot==null?Map.of():Map.copyOf(runtimeLoadSnapshot); }
}
