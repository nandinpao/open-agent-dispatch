package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection quota and message-rate protection for the Netty transport gateway.
 *
 * <p>The default application profile keeps this feature disabled for local development. The prod
 * profile enables it by default and deployments should tune limits according to expected Agent and Admin UI fan-in.</p>
 */
@ConfigurationProperties(prefix = "connection-protection")
public class ConnectionProtectionProperties {

    private boolean enabled = false;
    private int maxTcpConnections = 10000;
    private int maxTcpConnectionsPerRemoteAddress = 200;
    private int maxWebSocketSessions = 10000;
    private int maxWebSocketSessionsPerRemoteAddress = 200;
    private int maxTcpMessagesPerMinutePerRemoteAddress = 1200;
    private int maxWebSocketMessagesPerMinutePerRemoteAddress = 1200;
    private boolean closeOnRateLimit = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxTcpConnections() {
        return maxTcpConnections;
    }

    public void setMaxTcpConnections(int maxTcpConnections) {
        this.maxTcpConnections = maxTcpConnections <= 0 ? 10000 : maxTcpConnections;
    }

    public int getMaxTcpConnectionsPerRemoteAddress() {
        return maxTcpConnectionsPerRemoteAddress;
    }

    public void setMaxTcpConnectionsPerRemoteAddress(int maxTcpConnectionsPerRemoteAddress) {
        this.maxTcpConnectionsPerRemoteAddress = maxTcpConnectionsPerRemoteAddress <= 0 ? 200 : maxTcpConnectionsPerRemoteAddress;
    }

    public int getMaxWebSocketSessions() {
        return maxWebSocketSessions;
    }

    public void setMaxWebSocketSessions(int maxWebSocketSessions) {
        this.maxWebSocketSessions = maxWebSocketSessions <= 0 ? 10000 : maxWebSocketSessions;
    }

    public int getMaxWebSocketSessionsPerRemoteAddress() {
        return maxWebSocketSessionsPerRemoteAddress;
    }

    public void setMaxWebSocketSessionsPerRemoteAddress(int maxWebSocketSessionsPerRemoteAddress) {
        this.maxWebSocketSessionsPerRemoteAddress = maxWebSocketSessionsPerRemoteAddress <= 0 ? 200 : maxWebSocketSessionsPerRemoteAddress;
    }

    public int getMaxTcpMessagesPerMinutePerRemoteAddress() {
        return maxTcpMessagesPerMinutePerRemoteAddress;
    }

    public void setMaxTcpMessagesPerMinutePerRemoteAddress(int maxTcpMessagesPerMinutePerRemoteAddress) {
        this.maxTcpMessagesPerMinutePerRemoteAddress = maxTcpMessagesPerMinutePerRemoteAddress <= 0 ? 1200 : maxTcpMessagesPerMinutePerRemoteAddress;
    }

    public int getMaxWebSocketMessagesPerMinutePerRemoteAddress() {
        return maxWebSocketMessagesPerMinutePerRemoteAddress;
    }

    public void setMaxWebSocketMessagesPerMinutePerRemoteAddress(int maxWebSocketMessagesPerMinutePerRemoteAddress) {
        this.maxWebSocketMessagesPerMinutePerRemoteAddress = maxWebSocketMessagesPerMinutePerRemoteAddress <= 0 ? 1200 : maxWebSocketMessagesPerMinutePerRemoteAddress;
    }

    public boolean isCloseOnRateLimit() {
        return closeOnRateLimit;
    }

    public void setCloseOnRateLimit(boolean closeOnRateLimit) {
        this.closeOnRateLimit = closeOnRateLimit;
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxTcpConnections() {
        return maxTcpConnections;
    }

    public int maxTcpConnectionsPerRemoteAddress() {
        return maxTcpConnectionsPerRemoteAddress;
    }

    public int maxWebSocketSessions() {
        return maxWebSocketSessions;
    }

    public int maxWebSocketSessionsPerRemoteAddress() {
        return maxWebSocketSessionsPerRemoteAddress;
    }

    public int maxTcpMessagesPerMinutePerRemoteAddress() {
        return maxTcpMessagesPerMinutePerRemoteAddress;
    }

    public int maxWebSocketMessagesPerMinutePerRemoteAddress() {
        return maxWebSocketMessagesPerMinutePerRemoteAddress;
    }

    public boolean closeOnRateLimit() {
        return closeOnRateLimit;
    }
}
