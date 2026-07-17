package com.opensocket.aievent.gateway.netty.callback;

import java.time.OffsetDateTime;

/** Point-in-time counters for Netty -> Core task callback relay submissions. */
public record TaskCallbackRelayMetricsSnapshot(
        long total,
        long submitted,
        long failed,
        long disabled,
        long skipped,
        long rejected,
        OffsetDateTime generatedAt
) {
}
