package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.configuration.GatewayDynamicFixedDelayTask;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketAdminBroadcaster;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Periodically pushes node and gateway metrics to connected Admin WebSocket clients.
 * V41-C3R2N uses the authenticated local Runtime Configuration snapshot for enablement and cadence.
 */
@Component
public class AdminMetricsPushScheduler implements DisposableBean {

    private final AdminRuntimeConfigurationView runtimeConfiguration;
    private final AdminRuntimeMetricsService metricsService;
    private final WebSocketAdminBroadcaster adminBroadcaster;
    private final GatewayDynamicFixedDelayTask dynamicTask;

    public AdminMetricsPushScheduler(
            AdminRuntimeConfigurationView runtimeConfiguration,
            AdminRuntimeMetricsService metricsService,
            WebSocketAdminBroadcaster adminBroadcaster,
            TaskScheduler taskScheduler
    ) {
        this.runtimeConfiguration = runtimeConfiguration;
        this.metricsService = metricsService;
        this.adminBroadcaster = adminBroadcaster;
        this.dynamicTask = new GatewayDynamicFixedDelayTask(
                taskScheduler,
                "admin-metrics-push",
                this::pushMetrics,
                runtimeConfiguration::metricsPushInitialDelay,
                runtimeConfiguration::metricsPushInterval);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }

    public void pushMetrics() {
        if (!runtimeConfiguration.metricsPushEnabled() || adminBroadcaster.adminChannelCount() <= 0) return;
        adminBroadcaster.broadcastRealtime("NODE_METRICS_UPDATED", metricsService.nodeMetrics());
        adminBroadcaster.broadcastRealtime("GATEWAY_METRICS_UPDATED", metricsService.gatewayMetrics());
    }
}
