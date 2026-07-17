package com.opensocket.aievent.gateway.netty.delivery;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;

import java.time.OffsetDateTime;

/** Response for a transport-level command delivery attempt. */
public record CommandDeliveryResponse(
        String attemptId,
        String commandId,
        String traceId,
        String agentId,
        String gatewayNodeId,
        DeliveryStatus deliveryStatus,
        ConnectionType connectionType,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        OffsetDateTime deliveredAt,
        long durationMillis,
        String message,
        String taskId,
        String assignmentId,
        String dispatchRequestId,
        Integer attemptNo
) {
}
