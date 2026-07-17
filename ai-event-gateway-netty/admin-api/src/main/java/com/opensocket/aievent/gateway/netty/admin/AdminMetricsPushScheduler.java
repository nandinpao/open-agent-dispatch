package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketAdminBroadcaster;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically pushes node and gateway metrics to connected Admin WebSocket clients.
 *
 * <p>The default interval is five seconds. It can be tuned to three seconds for a more real-time
 * dashboard by setting ADMIN_METRICS_PUSH_INTERVAL_MS=3000.</p>
 */
@Component
public class AdminMetricsPushScheduler {

    private final AdminProperties adminProperties;
    private final AdminRuntimeMetricsService metricsService;
    private final WebSocketAdminBroadcaster adminBroadcaster;

    public AdminMetricsPushScheduler(
            AdminProperties adminProperties,
            AdminRuntimeMetricsService metricsService,
            WebSocketAdminBroadcaster adminBroadcaster
    ) {
        this.adminProperties = adminProperties;
        this.metricsService = metricsService;
        this.adminBroadcaster = adminBroadcaster;
    }

    @Scheduled(
            fixedDelayString = "${admin.metrics-push-interval-ms:5000}",
            initialDelayString = "${admin.metrics-push-initial-delay-ms:5000}"
    )
    public void pushMetrics() {
        if (!adminProperties.metricsPushEnabled() || adminBroadcaster.adminChannelCount() <= 0) {
            return;
        }
        adminBroadcaster.broadcastRealtime("NODE_METRICS_UPDATED", metricsService.nodeMetrics());
        adminBroadcaster.broadcastRealtime("GATEWAY_METRICS_UPDATED", metricsService.gatewayMetrics());
    }
}
