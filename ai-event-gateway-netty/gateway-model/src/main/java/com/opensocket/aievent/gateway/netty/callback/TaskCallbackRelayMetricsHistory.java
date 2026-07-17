package com.opensocket.aievent.gateway.netty.callback;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Recent Netty -> Core task callback relay attempts and status aggregation. */
public record TaskCallbackRelayMetricsHistory(
        List<TaskCallbackRelayAttemptRecord> records,
        Map<String, Long> byStatus,
        OffsetDateTime generatedAt
) {
}
