package com.opensocket.aievent.gateway.netty.inbound;

import java.time.OffsetDateTime;
import java.util.List;

/** Recent local inbound transport history for Admin diagnostics. */
public record InboundEventHistoryResponse(
        String gatewayNodeId,
        int requestedLimit,
        long historySize,
        List<InboundEventRecord> records,
        OffsetDateTime timestamp
) {
}
