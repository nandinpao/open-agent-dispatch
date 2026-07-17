package com.opensocket.aievent.gateway.netty.inbound;

import com.opensocket.aievent.gateway.netty.config.InboundEventCategory;

import java.time.OffsetDateTime;
import java.util.Map;

/** Local inbound receiver / optional forwarder counters. */
public record InboundEventMetrics(
        String gatewayNodeId,
        boolean forwardEnabled,
        String forwardEndpoint,
        long totalInboundEvents,
        long forwardedEvents,
        long forwardDisabledEvents,
        long forwardSkippedByCategoryEvents,
        long forwardFailedEvents,
        long forwardTimeoutEvents,
        long activeForwards,
        long historySize,
        Map<InboundForwardStatus, Long> byStatus,
        Map<InboundEventCategory, Long> byCategory,
        OffsetDateTime timestamp
) {
}
