package com.opensocket.aievent.gateway.netty.admin;

import java.time.Duration;
import java.util.Set;

import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

/** V41-C3R2N typed Gateway-local runtime view for Admin operational controls. */
public final class AdminRuntimeConfigurationView {
    public static final String SET_KEY = "RUNTIME/ADMIN/SYSTEM";
    public static final String METRICS_PUSH_ENABLED = "admin.metrics-push-enabled";
    public static final String METRICS_PUSH_INTERVAL_MS = "admin.metrics-push-interval-ms";
    public static final String RECENT_EVENT_LIMIT = "admin.recent-event-limit";
    public static final Set<String> ALL = Set.of(METRICS_PUSH_ENABLED, METRICS_PUSH_INTERVAL_MS, RECENT_EVENT_LIMIT);

    private final AdminProperties startup;
    private final GatewayRuntimeConfigurationSnapshotValues values;

    public AdminRuntimeConfigurationView(AdminProperties startup, GatewayRuntimeConfigurationSnapshotValues values) {
        this.startup = startup == null ? new AdminProperties() : startup;
        this.values = values;
    }

    public AdminRuntimeConfigurationView(AdminProperties startup) { this(startup, null); }

    public boolean metricsPushEnabled() {
        return values == null ? startup.metricsPushEnabled()
                : values.booleanValueOrStartup(SET_KEY, METRICS_PUSH_ENABLED, startup.metricsPushEnabled());
    }

    public long metricsPushIntervalMs() {
        long value = values == null ? startup.metricsPushIntervalMs()
                : values.longValueOrStartup(SET_KEY, METRICS_PUSH_INTERVAL_MS, startup.metricsPushIntervalMs());
        if (value < 250L || value > 3_600_000L) throw invalid(METRICS_PUSH_INTERVAL_MS, value);
        return value;
    }

    public Duration metricsPushInterval() { return Duration.ofMillis(metricsPushIntervalMs()); }

    /** Initial delay is deployment/bootstrap-only and intentionally not Runtime Configuration. */
    public Duration metricsPushInitialDelay() {
        long value = startup.metricsPushInitialDelayMs();
        if (value < 0L || value > 3_600_000L) throw invalid("admin.metrics-push-initial-delay-ms", value);
        return Duration.ofMillis(value);
    }

    public int recentEventLimit() {
        long value = values == null ? startup.recentEventLimit()
                : values.longValueOrStartup(SET_KEY, RECENT_EVENT_LIMIT, startup.recentEventLimit());
        if (value < 10L || value > 100_000L) throw invalid(RECENT_EVENT_LIMIT, value);
        return (int) value;
    }

    public boolean snapshotPresent() { return values != null && values.hasSnapshot(SET_KEY); }

    private static IllegalStateException invalid(String key, long value) {
        return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + key + " value=" + value);
    }
}
