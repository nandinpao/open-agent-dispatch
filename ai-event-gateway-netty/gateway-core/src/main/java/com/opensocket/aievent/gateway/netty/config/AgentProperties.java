package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Agent onboarding and lifecycle management.
 *
 * <p>This class uses JavaBean binding instead of a record so new security-related options can be
 * added without breaking existing tests and manual construction code that still calls
 * {@code new AgentProperties(timeoutSeconds, scanIntervalMs)}.</p>
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** Agent heartbeat timeout before the runtime status is marked as TIMEOUT. */
    private long heartbeatTimeoutSeconds = 30;

    /** Scheduler interval for scanning timed-out Agents. */
    private long timeoutScanIntervalMs = 5000;

    /** Enables onboarding-token validation for Agent registration over TCP and WebSocket. */
    private boolean authEnabled = false;

    /** Shared secret used to onboard Agents into this gateway node. */
    private String onboardingToken = "";

    /**
     * Additional accepted onboarding tokens used during rotation windows.
     *
     * <p>Use this for short-lived overlap only. The primary {@code onboardingToken} remains the
     * preferred token for new Agents.</p>
     */
    private List<String> additionalOnboardingTokens = new ArrayList<>();

    /** Enables optional token validation during Agent WebSocket handshake. */
    private boolean webSocketHandshakeAuthEnabled = false;

    /** Creates a properties object with local-development defaults. */
    public AgentProperties() {
    }

    /** Backward-compatible constructor used by tests and small helper utilities. */
    public AgentProperties(long heartbeatTimeoutSeconds, long timeoutScanIntervalMs) {
        setHeartbeatTimeoutSeconds(heartbeatTimeoutSeconds);
        setTimeoutScanIntervalMs(timeoutScanIntervalMs);
    }

    public long getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds <= 0 ? 30 : heartbeatTimeoutSeconds;
    }

    public long getTimeoutScanIntervalMs() {
        return timeoutScanIntervalMs;
    }

    public void setTimeoutScanIntervalMs(long timeoutScanIntervalMs) {
        this.timeoutScanIntervalMs = timeoutScanIntervalMs <= 0 ? 5000 : timeoutScanIntervalMs;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public String getOnboardingToken() {
        return onboardingToken;
    }

    public void setOnboardingToken(String onboardingToken) {
        this.onboardingToken = onboardingToken == null ? "" : onboardingToken.trim();
    }

    public List<String> getAdditionalOnboardingTokens() {
        return additionalOnboardingTokens;
    }

    public void setAdditionalOnboardingTokens(List<String> additionalOnboardingTokens) {
        if (additionalOnboardingTokens == null) {
            this.additionalOnboardingTokens = new ArrayList<>();
            return;
        }
        this.additionalOnboardingTokens = additionalOnboardingTokens.stream()
                .filter(token -> token != null && !token.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public boolean isWebSocketHandshakeAuthEnabled() {
        return webSocketHandshakeAuthEnabled;
    }

    public void setWebSocketHandshakeAuthEnabled(boolean webSocketHandshakeAuthEnabled) {
        this.webSocketHandshakeAuthEnabled = webSocketHandshakeAuthEnabled;
    }

    /** Record-style accessor kept for existing service code. */
    public long heartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    /** Record-style accessor kept for existing scheduler code. */
    public long timeoutScanIntervalMs() {
        return timeoutScanIntervalMs;
    }

    /** Record-style accessor used by the Agent onboarding guard. */
    public boolean authEnabled() {
        return authEnabled;
    }

    /** Record-style accessor used by the Agent onboarding guard. */
    public String onboardingToken() {
        return onboardingToken;
    }

    /** Record-style accessor used by the Agent onboarding guard for short-lived rotation windows. */
    public List<String> additionalOnboardingTokens() {
        return additionalOnboardingTokens;
    }

    /** Record-style accessor used by the Agent WebSocket handshake guard. */
    public boolean webSocketHandshakeAuthEnabled() {
        return webSocketHandshakeAuthEnabled;
    }
}
