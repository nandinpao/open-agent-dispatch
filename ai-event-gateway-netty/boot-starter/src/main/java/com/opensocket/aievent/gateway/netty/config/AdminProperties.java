package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the Admin API and Admin WebSocket features.
 *
 * <p>This class intentionally uses the JavaBean binding style instead of a Java record. Spring
 * Boot 4 can bind immutable constructor-based properties, but using a mutable JavaBean here avoids
 * constructor-selection issues when optional properties are added over time, such as metrics push
 * configuration. It also keeps backward-compatible record-like accessor methods such as
 * {@link #recentEventLimit()} so the rest of the codebase does not need to change.</p>
 */
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /** Maximum number of recent Admin events kept in the in-memory event store. */
    private int recentEventLimit = 200;

    /** Allowed exact CORS origins for the Admin UI local development and deployment endpoints. */
    private List<String> corsAllowedOrigins = List.of("http://localhost:3000", "http://127.0.0.1:3000");

    /** Optional CORS origin patterns, useful for controlled dev domains such as http://*.example.com:[3000]. */
    private List<String> corsAllowedOriginPatterns = List.of();

    /** Whether CORS responses should allow browser credentials. Keep false for bearer-token APIs. */
    private boolean corsAllowCredentials = false;

    /** Browser preflight cache duration in seconds. */
    private long corsMaxAgeSeconds = 3600;

    /** Enables or disables periodic metrics push events over the Admin WebSocket channel. */
    private boolean metricsPushEnabled = true;

    /** Enables machine-to-machine authentication for Netty Admin and cluster APIs. */
    private boolean machineAuthEnabled = false;

    /** Static bearer token used by Core and trusted server-side proxies to call Netty Admin APIs. */
    private String machineToken = "";

    /** Static bearer token used by internal cluster HTTP sync endpoints. */
    private String internalToken = "";

    /** Enables machine token validation during the Netty Admin WebSocket handshake. */
    private boolean machineWebSocketHandshakeAuthEnabled = true;

    /** Creates a properties object with safe defaults for local development. */
    public AdminProperties() {
    }

    /**
     * Backward-compatible constructor for unit tests and manually created property instances.
     *
     * <p>The previous implementation used a Java record, so tests could create an instance with
     * {@code new AdminProperties(limit, origins)}. After switching to JavaBean-style binding for
     * Spring Boot 4 configuration property compatibility, this constructor is kept to avoid breaking
     * existing test code and helper utilities.</p>
     *
     * @param recentEventLimit maximum number of Admin events to keep in memory
     * @param corsAllowedOrigins allowed CORS origins for the Admin UI
     */
    public AdminProperties(int recentEventLimit, List<String> corsAllowedOrigins) {
        setRecentEventLimit(recentEventLimit);
        setCorsAllowedOrigins(corsAllowedOrigins);
    }

    /**
     * Backward-compatible constructor for tests that need to explicitly control metrics push.
     *
     * @param recentEventLimit maximum number of Admin events to keep in memory
     * @param corsAllowedOrigins allowed CORS origins for the Admin UI
     * @param metricsPushEnabled whether periodic Admin WebSocket metrics push is enabled
     */
    public AdminProperties(int recentEventLimit, List<String> corsAllowedOrigins, boolean metricsPushEnabled) {
        this(recentEventLimit, corsAllowedOrigins);
        setMetricsPushEnabled(metricsPushEnabled);
    }

    public int getRecentEventLimit() {
        return recentEventLimit;
    }

    public void setRecentEventLimit(int recentEventLimit) {
        this.recentEventLimit = recentEventLimit <= 0 ? 200 : recentEventLimit;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()) {
            this.corsAllowedOrigins = List.of("http://localhost:3000", "http://127.0.0.1:3000");
            return;
        }
        this.corsAllowedOrigins = List.copyOf(corsAllowedOrigins);
    }

    public List<String> getCorsAllowedOriginPatterns() {
        return corsAllowedOriginPatterns;
    }

    public void setCorsAllowedOriginPatterns(List<String> corsAllowedOriginPatterns) {
        if (corsAllowedOriginPatterns == null || corsAllowedOriginPatterns.isEmpty()) {
            this.corsAllowedOriginPatterns = List.of();
            return;
        }
        this.corsAllowedOriginPatterns = List.copyOf(corsAllowedOriginPatterns);
    }

    public boolean isCorsAllowCredentials() {
        return corsAllowCredentials;
    }

    public void setCorsAllowCredentials(boolean corsAllowCredentials) {
        this.corsAllowCredentials = corsAllowCredentials;
    }

    public long getCorsMaxAgeSeconds() {
        return corsMaxAgeSeconds;
    }

    public void setCorsMaxAgeSeconds(long corsMaxAgeSeconds) {
        this.corsMaxAgeSeconds = corsMaxAgeSeconds <= 0 ? 3600 : corsMaxAgeSeconds;
    }

    public boolean isMetricsPushEnabled() {
        return metricsPushEnabled;
    }

    public void setMetricsPushEnabled(boolean metricsPushEnabled) {
        this.metricsPushEnabled = metricsPushEnabled;
    }

    public boolean isMachineAuthEnabled() { return machineAuthEnabled; }
    public void setMachineAuthEnabled(boolean machineAuthEnabled) { this.machineAuthEnabled = machineAuthEnabled; }
    public String getMachineToken() { return machineToken; }
    public void setMachineToken(String machineToken) { this.machineToken = machineToken == null ? "" : machineToken.trim(); }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken == null ? "" : internalToken.trim(); }
    public boolean isMachineWebSocketHandshakeAuthEnabled() { return machineWebSocketHandshakeAuthEnabled; }
    public void setMachineWebSocketHandshakeAuthEnabled(boolean enabled) { this.machineWebSocketHandshakeAuthEnabled = enabled; }

    /** Record-style accessor kept for existing service code. */
    public int recentEventLimit() {
        return recentEventLimit;
    }

    /** Record-style accessor kept for existing WebMVC CORS configuration code. */
    public List<String> corsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    /** Record-style accessor kept for existing WebMVC CORS configuration code. */
    public List<String> corsAllowedOriginPatterns() {
        return corsAllowedOriginPatterns;
    }

    /** Record-style accessor kept for existing WebMVC CORS configuration code. */
    public boolean corsAllowCredentials() {
        return corsAllowCredentials;
    }

    /** Record-style accessor kept for existing WebMVC CORS configuration code. */
    public long corsMaxAgeSeconds() {
        return corsMaxAgeSeconds;
    }

    /** Record-style accessor kept for the metrics scheduler. */
    public boolean metricsPushEnabled() {
        return metricsPushEnabled;
    }

    /** Record-style accessor used by the machine API token filter. */
    public boolean machineAuthEnabled() { return machineAuthEnabled; }
    public String machineToken() { return machineToken; }
    public String internalToken() { return internalToken; }
    public boolean machineWebSocketHandshakeAuthEnabled() { return machineWebSocketHandshakeAuthEnabled; }
}
