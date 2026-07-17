package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminMetricsResponse;

import java.time.OffsetDateTime;

/** Runtime metric snapshot for a single cluster node. */
public record ClusterNodeRuntimeMetricsResponse(
        String nodeId,
        double cpuUsagePercent,
        long memoryUsedMb,
        long memoryMaxMb,
        double memoryUsedPercent,
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
    public static ClusterNodeRuntimeMetricsResponse from(AdminMetricsResponse metrics) {
        if (metrics == null) {
            return empty("unknown");
        }
        return new ClusterNodeRuntimeMetricsResponse(
                metrics.nodeId(),
                metrics.cpuUsagePercent(),
                metrics.memoryUsedMb(),
                metrics.memoryMaxMb(),
                memoryPercent(metrics.memoryUsedMb(), metrics.memoryMaxMb()),
                metrics.nettyEventLoopThreads(),
                metrics.workerThreads(),
                metrics.transportQueueSize(),
                metrics.activeDeliveries(),
                metrics.inboundHistorySize(),
                metrics.activeInboundForwards(),
                metrics.inboundEventsPerMinute(),
                metrics.routedEventsPerMinute(),
                metrics.failedEventsPerMinute(),
                metrics.timestamp()
        );
    }

    public static ClusterNodeRuntimeMetricsResponse empty(String nodeId) {
        return new ClusterNodeRuntimeMetricsResponse(
                nodeId,
                0.0,
                0,
                0,
                0.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                OffsetDateTime.now()
        );
    }

    private static double memoryPercent(long usedMb, long maxMb) {
        if (usedMb <= 0 || maxMb <= 0) {
            return 0.0;
        }
        var value = ((double) usedMb / (double) maxMb) * 100.0;
        return Math.round(value * 10.0) / 10.0;
    }
}
