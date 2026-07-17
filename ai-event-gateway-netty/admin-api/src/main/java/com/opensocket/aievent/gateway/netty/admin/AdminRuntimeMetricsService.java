package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminGatewayMetricsPayload;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminMetricsResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminNodeMetricsPayload;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryTracker;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventTracker;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;

/** Builds runtime metrics for REST snapshots and Admin WebSocket metric updates. */
@Service
public class AdminRuntimeMetricsService {

    private static final long MB = 1024L * 1024L;

    private final GatewayProperties gatewayProperties;
    private final NettyServerProperties nettyServerProperties;
    private final AdminEventMetricsMeter eventMetricsMeter;
    private final CommandDeliveryTracker commandDeliveryTracker;
    private final InboundEventTracker inboundEventTracker;
    private volatile long lastCpuSampleWallNanos = -1L;
    private volatile long lastProcessCpuTimeNanos = -1L;

    public AdminRuntimeMetricsService(
            GatewayProperties gatewayProperties,
            NettyServerProperties nettyServerProperties,
            AdminEventMetricsMeter eventMetricsMeter,
            CommandDeliveryTracker commandDeliveryTracker,
            InboundEventTracker inboundEventTracker
    ) {
        this.gatewayProperties = gatewayProperties;
        this.nettyServerProperties = nettyServerProperties;
        this.eventMetricsMeter = eventMetricsMeter;
        this.commandDeliveryTracker = commandDeliveryTracker;
        this.inboundEventTracker = inboundEventTracker;
    }

    public AdminMetricsResponse snapshot() {
        var runtime = Runtime.getRuntime();
        return new AdminMetricsResponse(
                gatewayProperties.nodeId(),
                cpuUsagePercent(),
                memoryUsedMb(runtime),
                memoryMaxMb(runtime),
                nettyEventLoopThreads(),
                workerThreads(),
                transportQueueSize(),
                activeDeliveries(),
                inboundHistorySize(),
                activeInboundForwards(),
                eventMetricsMeter.inboundEventsPerMinute(),
                eventMetricsMeter.routedEventsPerMinute(),
                eventMetricsMeter.failedEventsPerMinute(),
                OffsetDateTime.now()
        );
    }

    public AdminNodeMetricsPayload nodeMetrics() {
        var snapshot = snapshot();
        return new AdminNodeMetricsPayload(
                snapshot.cpuUsagePercent(),
                snapshot.memoryUsedMb(),
                snapshot.memoryMaxMb(),
                snapshot.transportQueueSize(),
                snapshot.activeDeliveries(),
                snapshot.activeInboundForwards(),
                snapshot.nettyEventLoopThreads(),
                snapshot.workerThreads()
        );
    }

    public AdminGatewayMetricsPayload gatewayMetrics() {
        var snapshot = snapshot();
        return new AdminGatewayMetricsPayload(
                snapshot.cpuUsagePercent(),
                snapshot.memoryUsedMb(),
                snapshot.memoryMaxMb(),
                snapshot.transportQueueSize(),
                snapshot.workerThreads(),
                snapshot.inboundEventsPerMinute(),
                snapshot.routedEventsPerMinute(),
                snapshot.failedEventsPerMinute()
        );
    }

    private long transportQueueSize() {
        return commandDeliveryTracker.historySize();
    }

    private long activeDeliveries() {
        return commandDeliveryTracker.activeDeliveries();
    }

    private long inboundHistorySize() {
        return inboundEventTracker.historySize();
    }

    private long activeInboundForwards() {
        return inboundEventTracker.activeForwards();
    }

    private int nettyEventLoopThreads() {
        int defaultWorkerThreads = defaultNettyWorkerThreads();
        int total = 0;
        if (nettyServerProperties.tcp().enabled()) {
            total += 1 + defaultWorkerThreads;
        }
        if (nettyServerProperties.websocket().enabled()) {
            total += 1 + defaultWorkerThreads;
        }
        if (nettyServerProperties.cluster().enabled()) {
            total += 1;
        }
        return total;
    }

    private int defaultNettyWorkerThreads() {
        var configured = Integer.getInteger("io.netty.eventLoopThreads", 0);
        if (configured > 0) {
            return configured;
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
    }

    private int workerThreads() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }

    private double cpuUsagePercent() {
        var osBean = ManagementFactory.getOperatingSystemMXBean();
        double load = -1.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            load = sampledProcessCpuLoad(sunOsBean);
            if (load < 0) {
                load = sunOsBean.getProcessCpuLoad();
            }
        }
        if (load < 0 && osBean.getSystemLoadAverage() >= 0 && osBean.getAvailableProcessors() > 0) {
            load = osBean.getSystemLoadAverage() / osBean.getAvailableProcessors();
        }
        if (load < 0 || Double.isNaN(load) || Double.isInfinite(load)) {
            return 0.0;
        }
        return roundOneDecimal(Math.max(0.0, Math.min(100.0, load * 100.0)));
    }

    private synchronized double sampledProcessCpuLoad(com.sun.management.OperatingSystemMXBean osBean) {
        long processCpuTime = osBean.getProcessCpuTime();
        long wallTime = System.nanoTime();
        if (processCpuTime < 0) {
            return -1.0;
        }
        if (lastCpuSampleWallNanos < 0 || lastProcessCpuTimeNanos < 0) {
            lastCpuSampleWallNanos = wallTime;
            lastProcessCpuTimeNanos = processCpuTime;
            return -1.0;
        }
        long cpuDelta = processCpuTime - lastProcessCpuTimeNanos;
        long wallDelta = wallTime - lastCpuSampleWallNanos;
        lastCpuSampleWallNanos = wallTime;
        lastProcessCpuTimeNanos = processCpuTime;
        if (cpuDelta < 0 || wallDelta <= 0) {
            return -1.0;
        }
        var cores = Math.max(1, osBean.getAvailableProcessors());
        return Math.min(1.0, (double) cpuDelta / (double) wallDelta / cores);
    }

    private long memoryUsedMb(Runtime runtime) {
        return Math.max(0L, (runtime.totalMemory() - runtime.freeMemory()) / MB);
    }

    private long memoryMaxMb(Runtime runtime) {
        return runtime.maxMemory() <= 0 ? 0 : runtime.maxMemory() / MB;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
