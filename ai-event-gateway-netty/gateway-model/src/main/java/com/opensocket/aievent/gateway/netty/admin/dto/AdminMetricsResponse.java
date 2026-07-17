package com.opensocket.aievent.gateway.netty.admin.dto;

import java.time.OffsetDateTime;

/**
 * Flat Netty runtime metric snapshot used by the Admin UI during first dashboard load.
 *
 * <p>P6.3 removes task-oriented counters. Queue and active counts describe local transport
 * diagnostics only: command delivery history, inbound receiver history, and in-flight writes/forwards.</p>
 */
public record AdminMetricsResponse(
        String nodeId,
        double cpuUsagePercent,
        long memoryUsedMb,
        long memoryMaxMb,
        int nettyEventLoopThreads,
        int workerThreads,
        long transportQueueSize,
        long activeDeliveries,
        long inboundHistorySize,
        long activeInboundForwards,
        long inboundEventsPerMinute,
        long routedEventsPerMinute,
        long failedEventsPerMinute,
        OffsetDateTime timestamp
) {
}
