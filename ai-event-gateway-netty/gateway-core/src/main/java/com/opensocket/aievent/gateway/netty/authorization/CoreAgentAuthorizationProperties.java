package com.opensocket.aievent.gateway.netty.authorization;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.agent-authorization")
public class CoreAgentAuthorizationProperties {
    private boolean enabled = true;
    private String baseUrl = "http://localhost:18080";
    private String authorizePath = "/internal/agents/authorize-connection";
    private String securityEventPath = "/internal/agents/security-events";
    private long timeoutMs = 3000;
    private String authToken = "";
    private String authHeaderName = "X-Cluster-Token";
    private boolean failClosed = true;
    private boolean allowWhenDisabled = true;
    private int rejectedHistoryLimit = 500;
    private boolean duplicateRuntimeDetectionEnabled = true;
    private boolean duplicateRuntimeAutoEnforce;
    private boolean duplicateRuntimeAutoDisconnectAll;
    private boolean duplicateRuntimeRevokeCredentials;

    public boolean enabled() { return enabled; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String baseUrl() { return trimTrailingSlash(blank(baseUrl) ? "http://localhost:18080" : baseUrl.trim()); }
    public String getBaseUrl() { return baseUrl(); }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String authorizePath() { return normalizePath(authorizePath); }
    public String getAuthorizePath() { return authorizePath(); }
    public void setAuthorizePath(String authorizePath) { this.authorizePath = authorizePath; }
    public String securityEventPath() { return normalizePath(securityEventPath); }
    public String getSecurityEventPath() { return securityEventPath(); }
    public void setSecurityEventPath(String securityEventPath) { this.securityEventPath = securityEventPath; }
    public long timeoutMs() { return Math.max(100, timeoutMs); }
    public long getTimeoutMs() { return timeoutMs(); }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public String authToken() { return authToken == null ? "" : authToken.trim(); }
    public String getAuthToken() { return authToken(); }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String authHeaderName() { return blank(authHeaderName) ? "X-Cluster-Token" : authHeaderName.trim(); }
    public String getAuthHeaderName() { return authHeaderName(); }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
    public boolean failClosed() { return failClosed; }
    public boolean isFailClosed() { return failClosed; }
    public void setFailClosed(boolean failClosed) { this.failClosed = failClosed; }
    public boolean allowWhenDisabled() { return allowWhenDisabled; }
    public boolean isAllowWhenDisabled() { return allowWhenDisabled; }
    public void setAllowWhenDisabled(boolean allowWhenDisabled) { this.allowWhenDisabled = allowWhenDisabled; }
    public int rejectedHistoryLimit() { return Math.max(10, rejectedHistoryLimit); }
    public int getRejectedHistoryLimit() { return rejectedHistoryLimit(); }
    public void setRejectedHistoryLimit(int rejectedHistoryLimit) { this.rejectedHistoryLimit = rejectedHistoryLimit; }
    public boolean duplicateRuntimeDetectionEnabled() { return duplicateRuntimeDetectionEnabled; }
    public boolean isDuplicateRuntimeDetectionEnabled() { return duplicateRuntimeDetectionEnabled; }
    public void setDuplicateRuntimeDetectionEnabled(boolean duplicateRuntimeDetectionEnabled) { this.duplicateRuntimeDetectionEnabled = duplicateRuntimeDetectionEnabled; }
    public boolean duplicateRuntimeAutoEnforce() { return duplicateRuntimeAutoEnforce; }
    public boolean isDuplicateRuntimeAutoEnforce() { return duplicateRuntimeAutoEnforce; }
    public void setDuplicateRuntimeAutoEnforce(boolean duplicateRuntimeAutoEnforce) { this.duplicateRuntimeAutoEnforce = duplicateRuntimeAutoEnforce; }
    public boolean duplicateRuntimeAutoDisconnectAll() { return duplicateRuntimeAutoDisconnectAll; }
    public boolean isDuplicateRuntimeAutoDisconnectAll() { return duplicateRuntimeAutoDisconnectAll; }
    public void setDuplicateRuntimeAutoDisconnectAll(boolean duplicateRuntimeAutoDisconnectAll) { this.duplicateRuntimeAutoDisconnectAll = duplicateRuntimeAutoDisconnectAll; }
    public boolean duplicateRuntimeRevokeCredentials() { return duplicateRuntimeRevokeCredentials; }
    public boolean isDuplicateRuntimeRevokeCredentials() { return duplicateRuntimeRevokeCredentials; }
    public void setDuplicateRuntimeRevokeCredentials(boolean duplicateRuntimeRevokeCredentials) { this.duplicateRuntimeRevokeCredentials = duplicateRuntimeRevokeCredentials; }
    public boolean hasAuthToken() { return !blank(authToken); }
    public String authorizeUrl() { return baseUrl() + authorizePath(); }
    public String securityEventUrl() { return baseUrl() + securityEventPath(); }

    private static String normalizePath(String value) {
        if (blank(value)) return "/";
        var path = value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }
    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
