package com.opensocket.aievent.gateway.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * P6.14 gateway-side diagnostic marker. Core has a startup marker, but P6.13/P6.14
 * callback fixes live mostly in Netty; this marker proves which Gateway image and
 * callback ACK profile is actually running.
 */
@Component
public class GatewayDiagnosticsStartupLogger {
    private static final Logger log = LoggerFactory.getLogger(GatewayDiagnosticsStartupLogger.class);
    private final Environment environment;

    public GatewayDiagnosticsStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logGatewayDiagnosticsProfile() {
        log.info("gateway_diagnostics_logging_ready phase=P6_15 nodeId={} coreBaseUrl={} relayEnabled={} synchronousTerminalCallbacks={} logDir={} profiles={} markers=netty_callback_relay_sync_started,netty_callback_relay_sync_response,CALLBACK_CORE_ACCEPTED,task_runtime_view_loaded,task_failure_queue_loaded",
                property("GATEWAY_NODE_ID", "gateway-node-001"),
                firstNonBlank(property("GATEWAY_CORE_BASE_URL", null), property("CORE_BASE_URL", "http://core:18080")),
                property("GATEWAY_CORE_TASK_CALLBACK_RELAY_ENABLED", "true"),
                property("GATEWAY_CORE_TASK_CALLBACK_RELAY_SYNCHRONOUS_TERMINAL_CALLBACKS", "true"),
                property("LOG_DIR", "logs"),
                String.join(",", environment.getActiveProfiles()));
    }

    private String property(String name, String fallback) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
