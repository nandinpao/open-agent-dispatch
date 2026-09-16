package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Provider-neutral A0-R7 authoritative ExecutionAssignment. */
public record ExecutionAssignmentV206(
        String assignmentId,String tenantId,String shadowAssignmentId,String taskId,String planId,int planRevision,String stepId,
        String flowId,String routingDecisionId,String envelopeId,String bindingId,String providerId,String providerType,
        String agentPoolId,String selectedAgentId,String selectedSessionId,String selectedPeerInterfaceId,String selectedMcpServerId,
        String selectedAdapterId,String adapterType,String executionSafetyMode,String sideEffect,String writeSemantics,String humanApprovalRef,
        String leaseId,long fencingToken,OffsetDateTime leaseUntil,int attemptNumber,String previousAssignmentId,String authorityMode,
        String status,String assignmentReason,OffsetDateTime createdAt,OffsetDateTime releasedAt,String outcome) {}
