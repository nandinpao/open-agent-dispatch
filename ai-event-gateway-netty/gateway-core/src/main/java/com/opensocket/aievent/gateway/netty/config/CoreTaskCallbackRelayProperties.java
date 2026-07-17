package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional Task callback relay configuration. Netty remains the transport gateway and only relays
 * Agent task lifecycle callbacks to ai-event-gateway-core control-plane callback endpoints.
 */
@ConfigurationProperties(prefix = "gateway.core-task-callback-relay")
public class CoreTaskCallbackRelayProperties {
    private boolean enabled;
    private String baseUrl = "http://localhost:18080";
    private long timeoutMs = 3000;
    private String authToken = "";
    private String authHeaderName = "X-Cluster-Token";
    private String ackPath = "/internal/control-plane/tasks/{taskId}/ack";
    private String progressPath = "/internal/control-plane/tasks/{taskId}/progress";
    private String resultPath = "/internal/control-plane/tasks/{taskId}/result";
    private String errorPath = "/internal/control-plane/tasks/{taskId}/error";
    private boolean enrichGatewayIdentity = true;
    private boolean fillMissingAgentSessionId = true;
    private boolean requireDispatchContext = false;
    private boolean requireAssignmentId = false;
    /**
     * When enabled, terminal callbacks are relayed synchronously so the Agent can receive a
     * definitive Core acceptance ACK and safely remove durable pending callback state.
     */
    private boolean synchronousTerminalCallbacks = true;

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

    public String authHeaderName() { return blank(authHeaderName) ? "X-Cluster-Token" : authHeaderName.trim(); }
    public String getAuthHeaderName() { return authHeaderName(); }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }

    public String ackPath() { return normalizePath(blank(ackPath) ? "/internal/control-plane/tasks/{taskId}/ack" : ackPath.trim()); }
    public String getAckPath() { return ackPath(); }
    public void setAckPath(String ackPath) { this.ackPath = ackPath; }

    public String progressPath() { return normalizePath(blank(progressPath) ? "/internal/control-plane/tasks/{taskId}/progress" : progressPath.trim()); }
    public String getProgressPath() { return progressPath(); }
    public void setProgressPath(String progressPath) { this.progressPath = progressPath; }

    public String resultPath() { return normalizePath(blank(resultPath) ? "/internal/control-plane/tasks/{taskId}/result" : resultPath.trim()); }
    public String getResultPath() { return resultPath(); }
    public void setResultPath(String resultPath) { this.resultPath = resultPath; }

    public String errorPath() { return normalizePath(blank(errorPath) ? "/internal/control-plane/tasks/{taskId}/error" : errorPath.trim()); }
    public String getErrorPath() { return errorPath(); }
    public void setErrorPath(String errorPath) { this.errorPath = errorPath; }

    public boolean enrichGatewayIdentity() { return enrichGatewayIdentity; }
    public boolean isEnrichGatewayIdentity() { return enrichGatewayIdentity(); }
    public void setEnrichGatewayIdentity(boolean enrichGatewayIdentity) { this.enrichGatewayIdentity = enrichGatewayIdentity; }

    public boolean fillMissingAgentSessionId() { return fillMissingAgentSessionId; }
    public boolean isFillMissingAgentSessionId() { return fillMissingAgentSessionId(); }
    public void setFillMissingAgentSessionId(boolean fillMissingAgentSessionId) { this.fillMissingAgentSessionId = fillMissingAgentSessionId; }

    public boolean requireDispatchContext() { return requireDispatchContext; }
    public boolean isRequireDispatchContext() { return requireDispatchContext(); }
    public void setRequireDispatchContext(boolean requireDispatchContext) { this.requireDispatchContext = requireDispatchContext; }

    public boolean requireAssignmentId() { return requireAssignmentId; }
    public boolean isRequireAssignmentId() { return requireAssignmentId(); }
    public void setRequireAssignmentId(boolean requireAssignmentId) { this.requireAssignmentId = requireAssignmentId; }

    public boolean synchronousTerminalCallbacks() { return synchronousTerminalCallbacks; }
    public boolean isSynchronousTerminalCallbacks() { return synchronousTerminalCallbacks(); }
    public void setSynchronousTerminalCallbacks(boolean synchronousTerminalCallbacks) { this.synchronousTerminalCallbacks = synchronousTerminalCallbacks; }

    public boolean hasAuthToken() { return !blank(authToken()); }

    public String callbackUrl(String path, String taskId) {
        return baseUrl() + path.replace("{taskId}", urlEncode(taskId));
    }

    private static String normalizePath(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
