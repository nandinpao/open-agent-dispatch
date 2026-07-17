package com.opensocket.aievent.gateway.netty.admin.dto;

/** Node-level Netty runtime metrics pushed to Admin WebSocket clients. */
public record AdminNodeMetricsPayload(
        double cpuUsagePercent,
        long memoryUsedMb,
        long memoryMaxMb,
        long transportQueueSize,
        long activeDeliveries,
        long activeInboundForwards,
        int nettyEventLoopThreads,
        int workerThreads
) {
}
