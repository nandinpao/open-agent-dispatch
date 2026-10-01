package com.opensocket.aievent.gateway.netty.agent;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

/**
 * C3R2C typed local-snapshot view for Agent transport lifecycle tuning.
 *
 * <p>Only operational lifecycle values belong here. Authentication enforcement switches remain
 * startup security invariants and are deliberately excluded from Runtime Configuration.</p>
 */
@Component
public final class AgentRuntimeConfigurationView {
    public static final String SET_KEY = "RUNTIME/AGENT/SYSTEM";
    public static final String HEARTBEAT_TIMEOUT_SECONDS = "agent.heartbeat-timeout-seconds";
    public static final String TIMEOUT_SCAN_INTERVAL_MS = "agent.timeout-scan-interval-ms";
    public static final Set<String> ALL = Set.of(HEARTBEAT_TIMEOUT_SECONDS, TIMEOUT_SCAN_INTERVAL_MS);

    private final AgentProperties startup;
    private final GatewayRuntimeConfigurationSnapshotValues values;

    @Autowired
    public AgentRuntimeConfigurationView(
            AgentProperties startup,
            GatewayRuntimeConfigurationSnapshotValues values) {
        this.startup = startup == null ? new AgentProperties() : startup;
        this.values = values;
    }

    /** Startup-only compatibility constructor for focused unit tests. */
    public AgentRuntimeConfigurationView(AgentProperties startup) {
        this.startup = startup == null ? new AgentProperties() : startup;
        this.values = null;
    }

    public Duration heartbeatTimeout() {
        long seconds = value(HEARTBEAT_TIMEOUT_SECONDS, startup.heartbeatTimeoutSeconds());
        if (seconds < 5 || seconds > 3600) {
            throw invalid(HEARTBEAT_TIMEOUT_SECONDS, seconds);
        }
        return Duration.ofSeconds(seconds);
    }

    public Duration timeoutScanInterval() {
        long millis = value(TIMEOUT_SCAN_INTERVAL_MS, startup.timeoutScanIntervalMs());
        if (millis < 250 || millis > 3_600_000) {
            throw invalid(TIMEOUT_SCAN_INTERVAL_MS, millis);
        }
        return Duration.ofMillis(millis);
    }

    public long heartbeatTimeoutSeconds() {
        return heartbeatTimeout().toSeconds();
    }

    public long timeoutScanIntervalMs() {
        return timeoutScanInterval().toMillis();
    }

    public boolean snapshotPresent() {
        return values != null && values.hasSnapshot(SET_KEY);
    }

    private long value(String key, long fallback) {
        if (values == null) return fallback;
        return values.longValueOrStartup(SET_KEY, key, fallback);
    }

    private static IllegalStateException invalid(String key, long value) {
        return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + key + " value=" + value);
    }
}
