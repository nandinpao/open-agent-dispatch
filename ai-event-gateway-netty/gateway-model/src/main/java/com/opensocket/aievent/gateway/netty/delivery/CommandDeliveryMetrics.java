package com.opensocket.aievent.gateway.netty.delivery;

import java.time.OffsetDateTime;
import java.util.Map;

/** Local in-memory command-delivery counters for the current gateway node. */
public record CommandDeliveryMetrics(
        String gatewayNodeId,
        long totalAttempts,
        long deliveredAttempts,
        long failedAttempts,
        long agentNotConnectedAttempts,
        long connectionNotWritableAttempts,
        long timeoutAttempts,
        long invalidCommandAttempts,
        long missingDispatchContextAttempts,
        long agentNotAuthorizedAttempts,
        long activeDeliveries,
        long historySize,
        Map<DeliveryStatus, Long> byStatus,
        OffsetDateTime generatedAt
) {
}
