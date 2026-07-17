package com.opensocket.aievent.gateway.netty.admin.dto;

/** Gateway-level Netty runtime and event throughput metrics pushed to Admin WebSocket clients. */
public record AdminGatewayMetricsPayload(
        double cpuUsagePercent,
        long memoryUsedMb,
        long memoryMaxMb,
        long transportQueueSize,
        int workerThreads,
        long inboundEventsPerMinute,
        long routedEventsPerMinute,
        long failedEventsPerMinute
) {
}
