package com.opensocket.aievent.gateway.netty.delivery;

import java.time.OffsetDateTime;
import java.util.List;

/** Bounded local command-delivery history response for Admin UI diagnostics. */
public record CommandDeliveryHistoryResponse(
        String gatewayNodeId,
        int limit,
        long totalStored,
        List<CommandDeliveryAttemptRecord> records,
        OffsetDateTime generatedAt
) {
}
