package com.opensocket.aievent.gateway.netty.runtime;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.config.ConnectionProtectionProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

/** C3R2I typed runtime view for request-time connection protection limits. */
@Component
public final class ConnectionProtectionRuntimeConfigurationView {
    public static final String SET_KEY = "RUNTIME/CONNECTION_PROTECTION/SYSTEM";
    public static final String CLOSE_ON_RATE_LIMIT = "connection-protection.close-on-rate-limit";
    public static final String MAX_TCP_CONNECTIONS = "connection-protection.max-tcp-connections";
    public static final String MAX_TCP_CONNECTIONS_PER_REMOTE = "connection-protection.max-tcp-connections-per-remote-address";
    public static final String MAX_WS_SESSIONS = "connection-protection.max-web-socket-sessions";
    public static final String MAX_WS_SESSIONS_PER_REMOTE = "connection-protection.max-web-socket-sessions-per-remote-address";
    public static final String MAX_TCP_MESSAGES_PER_MINUTE_PER_REMOTE = "connection-protection.max-tcp-messages-per-minute-per-remote-address";
    public static final String MAX_WS_MESSAGES_PER_MINUTE_PER_REMOTE = "connection-protection.max-web-socket-messages-per-minute-per-remote-address";
    public static final Set<String> ALL = Set.of(
            CLOSE_ON_RATE_LIMIT,
            MAX_TCP_CONNECTIONS,
            MAX_TCP_CONNECTIONS_PER_REMOTE,
            MAX_WS_SESSIONS,
            MAX_WS_SESSIONS_PER_REMOTE,
            MAX_TCP_MESSAGES_PER_MINUTE_PER_REMOTE,
            MAX_WS_MESSAGES_PER_MINUTE_PER_REMOTE);

    private final ConnectionProtectionProperties startup;
    private final GatewayRuntimeConfigurationSnapshotValues values;

    @Autowired
    public ConnectionProtectionRuntimeConfigurationView(
            ConnectionProtectionProperties startup,
            GatewayRuntimeConfigurationSnapshotValues values) {
        this.startup = startup == null ? new ConnectionProtectionProperties() : startup;
        this.values = values;
    }

    public ConnectionProtectionRuntimeConfigurationView(ConnectionProtectionProperties startup) {
        this.startup = startup == null ? new ConnectionProtectionProperties() : startup;
        this.values = null;
    }

    public boolean closeOnRateLimit() { return bool(CLOSE_ON_RATE_LIMIT, startup.closeOnRateLimit()); }
    public int maxTcpConnections() { return bounded(MAX_TCP_CONNECTIONS, integer(MAX_TCP_CONNECTIONS, startup.maxTcpConnections()), 1, 1_000_000); }
    public int maxTcpConnectionsPerRemoteAddress() { return bounded(MAX_TCP_CONNECTIONS_PER_REMOTE, integer(MAX_TCP_CONNECTIONS_PER_REMOTE, startup.maxTcpConnectionsPerRemoteAddress()), 1, 100_000); }
    public int maxWebSocketSessions() { return bounded(MAX_WS_SESSIONS, integer(MAX_WS_SESSIONS, startup.maxWebSocketSessions()), 1, 1_000_000); }
    public int maxWebSocketSessionsPerRemoteAddress() { return bounded(MAX_WS_SESSIONS_PER_REMOTE, integer(MAX_WS_SESSIONS_PER_REMOTE, startup.maxWebSocketSessionsPerRemoteAddress()), 1, 100_000); }
    public int maxTcpMessagesPerMinutePerRemoteAddress() { return bounded(MAX_TCP_MESSAGES_PER_MINUTE_PER_REMOTE, integer(MAX_TCP_MESSAGES_PER_MINUTE_PER_REMOTE, startup.maxTcpMessagesPerMinutePerRemoteAddress()), 1, 10_000_000); }
    public int maxWebSocketMessagesPerMinutePerRemoteAddress() { return bounded(MAX_WS_MESSAGES_PER_MINUTE_PER_REMOTE, integer(MAX_WS_MESSAGES_PER_MINUTE_PER_REMOTE, startup.maxWebSocketMessagesPerMinutePerRemoteAddress()), 1, 10_000_000); }

    private boolean bool(String key, boolean fallback) {
        return values == null ? fallback : values.booleanValueOrStartup(SET_KEY, key, fallback);
    }

    private int integer(String key, int fallback) {
        long value = values == null ? fallback : values.longValueOrStartup(SET_KEY, key, fallback);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw invalid(key, value);
        return (int) value;
    }

    private static int bounded(String key, int value, int min, int max) {
        if (value < min || value > max) throw invalid(key, value);
        return value;
    }

    private static IllegalStateException invalid(String key, long value) {
        return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + key + " value=" + value);
    }
}
