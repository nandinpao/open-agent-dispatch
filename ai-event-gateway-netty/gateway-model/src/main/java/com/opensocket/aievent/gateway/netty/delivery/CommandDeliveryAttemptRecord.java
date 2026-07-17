package com.opensocket.aievent.gateway.netty.delivery;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;

import java.time.OffsetDateTime;

/** Immutable transport-level command delivery history item. Payload is intentionally not stored. */
public record CommandDeliveryAttemptRecord(
        String attemptId,
        String commandId,
        String traceId,
        String agentId,
        String gatewayNodeId,
        MessageType messageType,
        String issuedBy,
        String taskId,
        String assignmentId,
        String dispatchRequestId,
        Integer attemptNo,
        ConnectionType connectionType,
        DeliveryStatus deliveryStatus,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        long durationMillis,
        String message
) {
}
