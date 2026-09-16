package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Durable A0-R7 outbox intent. Network delivery is forbidden until this record commits. */
public record ExecutionDispatchIntentV206(
        String intentId,String tenantId,String assignmentId,String leaseId,long fencingToken,String taskId,String planId,int planRevision,
        String stepId,String flowId,String bindingId,String providerId,String providerType,String selectedAdapterId,String adapterType,
        String executionSafetyMode,String sideEffect,String writeSemantics,Map<String,Object> payload,String status,int attemptCount,
        String claimedBy,String claimToken,OffsetDateTime claimUntil,OffsetDateTime sendStartedAt,OffsetDateTime handedOffAt,
        String externalExecutionRef,OffsetDateTime deliveryUnknownSince,String lastErrorCode,String lastErrorMessage,
        OffsetDateTime createdAt,OffsetDateTime updatedAt) {
    public ExecutionDispatchIntentV206 { payload=payload==null?Map.of():Map.copyOf(payload); }
}
