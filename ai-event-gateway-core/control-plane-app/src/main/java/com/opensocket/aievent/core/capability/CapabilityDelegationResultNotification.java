package com.opensocket.aievent.core.capability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Durable-result / transient-payload envelope used only for Stage 4 parent-Agent continuation. */
public record CapabilityDelegationResultNotification(
        String tenantId,
        String delegationId,
        String parentTaskId,
        String childTaskId,
        String capabilityCode,
        String operation,
        String delegationStatus,
        String resultStatus,
        String resultMessage,
        String errorCode,
        String errorMessage,
        String completedByAgentId,
        String callbackId,
        String payloadHash,
        String correlationId,
        String parentAssignmentId,
        String parentAgentId,
        String parentAgentSessionId,
        String parentGatewayNodeId,
        String notificationId,
        Map<String, Object> resultPayload,
        boolean alreadyDelivered) {
    public CapabilityDelegationResultNotification {
        resultPayload = resultPayload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(resultPayload));
    }
}
