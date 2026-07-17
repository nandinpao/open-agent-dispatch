package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional Core Global Agent Directory synchronization settings.
 *
 * <p>When enabled, ai-event-gateway-netty reports Gateway registration, Gateway heartbeat, Agent
 * connected, Agent heartbeat, Agent disconnected, and periodic full Agent snapshots to
 * ai-event-gateway-core. It does not make Netty own assignment or durable task state.</p>
 */
@ConfigurationProperties(prefix = "gateway.core-directory-sync")
public class CoreDirectorySyncProperties {
    private boolean enabled;
    private String baseUrl = "http://localhost:18080";
    private long timeoutMs = 3000;
    private String authToken = "";
    private String authHeaderName = "X-Cluster-Token";
    private boolean registerOnStartup = true;
    private long gatewayHeartbeatIntervalMs = 15000;
    private long snapshotIntervalMs = 60000;
    private long gatewayLeaseTtlSeconds = 45;
    private long agentLeaseTtlSeconds = 45;
    private int defaultAgentMaxConcurrentTasks = 1;
    private int defaultAgentHealthScore = 100;

    public CoreDirectorySyncProperties() {
    }

    public boolean enabled() { return enabled; }
    public boolean isEnabled() { return enabled(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String baseUrl() {
        var value = blank(baseUrl) ? "http://localhost:18080" : baseUrl.trim();
        return trimTrailingSlash(value);
    }
    public String getBaseUrl() { return baseUrl(); }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public long timeoutMs() { return timeoutMs <= 0 ? 3000 : timeoutMs; }
    public long getTimeoutMs() { return timeoutMs(); }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public String authToken() { return blank(authToken) ? "" : authToken.trim(); }
    public String getAuthToken() { return authToken(); }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public boolean hasAuthToken() { return !blank(authToken()); }

    public String authHeaderName() { return blank(authHeaderName) ? "X-Cluster-Token" : authHeaderName.trim(); }
    public String getAuthHeaderName() { return authHeaderName(); }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }

    public boolean registerOnStartup() { return registerOnStartup; }
    public boolean isRegisterOnStartup() { return registerOnStartup(); }
    public void setRegisterOnStartup(boolean registerOnStartup) { this.registerOnStartup = registerOnStartup; }

    public long gatewayHeartbeatIntervalMs() { return gatewayHeartbeatIntervalMs <= 0 ? 15000 : gatewayHeartbeatIntervalMs; }
    public long getGatewayHeartbeatIntervalMs() { return gatewayHeartbeatIntervalMs(); }
    public void setGatewayHeartbeatIntervalMs(long gatewayHeartbeatIntervalMs) { this.gatewayHeartbeatIntervalMs = gatewayHeartbeatIntervalMs; }

    public long snapshotIntervalMs() { return snapshotIntervalMs <= 0 ? 60000 : snapshotIntervalMs; }
    public long getSnapshotIntervalMs() { return snapshotIntervalMs(); }
    public void setSnapshotIntervalMs(long snapshotIntervalMs) { this.snapshotIntervalMs = snapshotIntervalMs; }

    public long gatewayLeaseTtlSeconds() { return gatewayLeaseTtlSeconds <= 0 ? 45 : gatewayLeaseTtlSeconds; }
    public long getGatewayLeaseTtlSeconds() { return gatewayLeaseTtlSeconds(); }
    public void setGatewayLeaseTtlSeconds(long gatewayLeaseTtlSeconds) { this.gatewayLeaseTtlSeconds = gatewayLeaseTtlSeconds; }

    public long agentLeaseTtlSeconds() { return agentLeaseTtlSeconds <= 0 ? 45 : agentLeaseTtlSeconds; }
    public long getAgentLeaseTtlSeconds() { return agentLeaseTtlSeconds(); }
    public void setAgentLeaseTtlSeconds(long agentLeaseTtlSeconds) { this.agentLeaseTtlSeconds = agentLeaseTtlSeconds; }

    public int defaultAgentMaxConcurrentTasks() { return defaultAgentMaxConcurrentTasks <= 0 ? 1 : Math.min(defaultAgentMaxConcurrentTasks, 1000); }
    public int getDefaultAgentMaxConcurrentTasks() { return defaultAgentMaxConcurrentTasks(); }
    public void setDefaultAgentMaxConcurrentTasks(int defaultAgentMaxConcurrentTasks) { this.defaultAgentMaxConcurrentTasks = defaultAgentMaxConcurrentTasks; }

    public int defaultAgentHealthScore() {
        if (defaultAgentHealthScore <= 0) {
            return 100;
        }
        return Math.min(defaultAgentHealthScore, 100);
    }
    public int getDefaultAgentHealthScore() { return defaultAgentHealthScore(); }
    public void setDefaultAgentHealthScore(int defaultAgentHealthScore) { this.defaultAgentHealthScore = defaultAgentHealthScore; }

    public String gatewayRegisterUrl() { return baseUrl() + "/internal/gateway-nodes/register"; }
    public String gatewayHeartbeatUrl(String gatewayNodeId) {
        return baseUrl() + "/internal/gateway-nodes/" + safePathSegment(gatewayNodeId) + "/heartbeat";
    }
    public String agentConnectedUrl(String gatewayNodeId, String agentId) {
        return baseUrl() + "/internal/gateway-nodes/" + safePathSegment(gatewayNodeId) + "/agents/" + safePathSegment(agentId) + "/connected";
    }
    public String agentHeartbeatUrl(String gatewayNodeId, String agentId) {
        return baseUrl() + "/internal/gateway-nodes/" + safePathSegment(gatewayNodeId) + "/agents/" + safePathSegment(agentId) + "/heartbeat";
    }
    public String agentDisconnectedUrl(String gatewayNodeId, String agentId) {
        return baseUrl() + "/internal/gateway-nodes/" + safePathSegment(gatewayNodeId) + "/agents/" + safePathSegment(agentId) + "/disconnected";
    }
    public String gatewaySnapshotUrl(String gatewayNodeId) {
        return baseUrl() + "/internal/gateway-nodes/" + safePathSegment(gatewayNodeId) + "/agents/snapshot";
    }

    private static String safePathSegment(String value) {
        if (blank(value)) {
            return "";
        }
        return value.trim().replace("/", "");
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
