package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional inbound-event forwarder configuration.
 *
 * <p>This belongs to the Netty transport gateway only as an HTTP forwarding adapter. It does not
 * make ai-event-gateway-netty own Core, task state, routing, deduplication, databases, queues, or
 * business decisions.</p>
 *
 * <p>Keep this type as a regular JavaBean instead of a record. Spring Boot 4 can bind records by
 * constructor, but the backward-compatible custom constructor used by tests and manual
 * instantiation makes constructor selection ambiguous. A no-arg JavaBean avoids startup failures
 * such as "No default constructor found" while preserving record-style accessor methods used by
 * the existing code.</p>
 */
@ConfigurationProperties(prefix = "gateway.core-forward")
public class CoreForwardProperties {
    private boolean enabled;
    private String baseUrl = "http://localhost:18080";
    private String inboundPath = "/internal/core/inbound-events";
    private long timeoutMs = 3000;
    private String authToken = "";
    private int historyLimit = 500;
    private boolean recordBusinessEvents = true;
    private boolean recordTaskLifecycleEvents = true;
    private boolean recordTransportSignals;
    private boolean recordHeartbeatSignals;
    private boolean recordSystemSignals;
    private boolean forwardBusinessEvents = true;
    private boolean forwardTaskLifecycleEvents = true;
    private boolean forwardTransportSignals;
    private boolean forwardHeartbeatSignals;
    private boolean forwardSystemSignals;

    public CoreForwardProperties() {
        // Required by Spring Boot JavaBean configuration-property binding.
    }

    public CoreForwardProperties(
            boolean enabled,
            String baseUrl,
            String inboundPath,
            long timeoutMs,
            String authToken,
            int historyLimit
    ) {
        this(
                enabled,
                baseUrl,
                inboundPath,
                timeoutMs,
                authToken,
                historyLimit,
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false
        );
    }

    public CoreForwardProperties(
            boolean enabled,
            String baseUrl,
            String inboundPath,
            long timeoutMs,
            String authToken,
            int historyLimit,
            boolean recordBusinessEvents,
            boolean recordTaskLifecycleEvents,
            boolean recordTransportSignals,
            boolean recordHeartbeatSignals,
            boolean recordSystemSignals,
            boolean forwardBusinessEvents,
            boolean forwardTaskLifecycleEvents,
            boolean forwardTransportSignals,
            boolean forwardHeartbeatSignals,
            boolean forwardSystemSignals
    ) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.inboundPath = inboundPath;
        this.timeoutMs = timeoutMs;
        this.authToken = authToken;
        this.historyLimit = historyLimit;
        this.recordBusinessEvents = recordBusinessEvents;
        this.recordTaskLifecycleEvents = recordTaskLifecycleEvents;
        this.recordTransportSignals = recordTransportSignals;
        this.recordHeartbeatSignals = recordHeartbeatSignals;
        this.recordSystemSignals = recordSystemSignals;
        this.forwardBusinessEvents = forwardBusinessEvents;
        this.forwardTaskLifecycleEvents = forwardTaskLifecycleEvents;
        this.forwardTransportSignals = forwardTransportSignals;
        this.forwardHeartbeatSignals = forwardHeartbeatSignals;
        this.forwardSystemSignals = forwardSystemSignals;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isEnabled() {
        return enabled();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String baseUrl() {
        var value = blank(baseUrl) ? "http://localhost:18080" : baseUrl.trim();
        return trimTrailingSlash(value);
    }

    public String getBaseUrl() {
        return baseUrl();
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String inboundPath() {
        var value = blank(inboundPath) ? "/internal/core/inbound-events" : inboundPath.trim();
        return normalizePath(value);
    }

    public String getInboundPath() {
        return inboundPath();
    }

    public void setInboundPath(String inboundPath) {
        this.inboundPath = inboundPath;
    }

    public long timeoutMs() {
        return timeoutMs <= 0 ? 3000 : timeoutMs;
    }

    public long getTimeoutMs() {
        return timeoutMs();
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String authToken() {
        return blank(authToken) ? "" : authToken.trim();
    }

    public String getAuthToken() {
        return authToken();
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public int historyLimit() {
        return historyLimit <= 0 ? 500 : Math.min(historyLimit, 10000);
    }

    public int getHistoryLimit() {
        return historyLimit();
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }

    public boolean recordBusinessEvents() {
        return recordBusinessEvents;
    }

    public boolean isRecordBusinessEvents() {
        return recordBusinessEvents();
    }

    public void setRecordBusinessEvents(boolean recordBusinessEvents) {
        this.recordBusinessEvents = recordBusinessEvents;
    }

    public boolean recordTaskLifecycleEvents() {
        return recordTaskLifecycleEvents;
    }

    public boolean isRecordTaskLifecycleEvents() {
        return recordTaskLifecycleEvents();
    }

    public void setRecordTaskLifecycleEvents(boolean recordTaskLifecycleEvents) {
        this.recordTaskLifecycleEvents = recordTaskLifecycleEvents;
    }

    public boolean recordTransportSignals() {
        return recordTransportSignals;
    }

    public boolean isRecordTransportSignals() {
        return recordTransportSignals();
    }

    public void setRecordTransportSignals(boolean recordTransportSignals) {
        this.recordTransportSignals = recordTransportSignals;
    }

    public boolean recordHeartbeatSignals() {
        return recordHeartbeatSignals;
    }

    public boolean isRecordHeartbeatSignals() {
        return recordHeartbeatSignals();
    }

    public void setRecordHeartbeatSignals(boolean recordHeartbeatSignals) {
        this.recordHeartbeatSignals = recordHeartbeatSignals;
    }

    public boolean recordSystemSignals() {
        return recordSystemSignals;
    }

    public boolean isRecordSystemSignals() {
        return recordSystemSignals();
    }

    public void setRecordSystemSignals(boolean recordSystemSignals) {
        this.recordSystemSignals = recordSystemSignals;
    }

    public boolean forwardBusinessEvents() {
        return forwardBusinessEvents;
    }

    public boolean isForwardBusinessEvents() {
        return forwardBusinessEvents();
    }

    public void setForwardBusinessEvents(boolean forwardBusinessEvents) {
        this.forwardBusinessEvents = forwardBusinessEvents;
    }

    public boolean forwardTaskLifecycleEvents() {
        return forwardTaskLifecycleEvents;
    }

    public boolean isForwardTaskLifecycleEvents() {
        return forwardTaskLifecycleEvents();
    }

    public void setForwardTaskLifecycleEvents(boolean forwardTaskLifecycleEvents) {
        this.forwardTaskLifecycleEvents = forwardTaskLifecycleEvents;
    }

    public boolean forwardTransportSignals() {
        return forwardTransportSignals;
    }

    public boolean isForwardTransportSignals() {
        return forwardTransportSignals();
    }

    public void setForwardTransportSignals(boolean forwardTransportSignals) {
        this.forwardTransportSignals = forwardTransportSignals;
    }

    public boolean forwardHeartbeatSignals() {
        return forwardHeartbeatSignals;
    }

    public boolean isForwardHeartbeatSignals() {
        return forwardHeartbeatSignals();
    }

    public void setForwardHeartbeatSignals(boolean forwardHeartbeatSignals) {
        this.forwardHeartbeatSignals = forwardHeartbeatSignals;
    }

    public boolean forwardSystemSignals() {
        return forwardSystemSignals;
    }

    public boolean isForwardSystemSignals() {
        return forwardSystemSignals();
    }

    public void setForwardSystemSignals(boolean forwardSystemSignals) {
        this.forwardSystemSignals = forwardSystemSignals;
    }

    public String endpointUrl() {
        return baseUrl() + inboundPath();
    }

    public boolean hasAuthToken() {
        return !blank(authToken());
    }

    public boolean shouldRecord(InboundEventCategory category) {
        return switch (category == null ? InboundEventCategory.SYSTEM_SIGNAL : category) {
            case BUSINESS_EVENT -> recordBusinessEvents;
            case TASK_LIFECYCLE_EVENT -> recordTaskLifecycleEvents;
            case TRANSPORT_SIGNAL -> recordTransportSignals;
            case HEARTBEAT_SIGNAL -> recordHeartbeatSignals;
            case SYSTEM_SIGNAL -> recordSystemSignals;
        };
    }

    public boolean shouldForward(InboundEventCategory category) {
        return switch (category == null ? InboundEventCategory.SYSTEM_SIGNAL : category) {
            case BUSINESS_EVENT -> forwardBusinessEvents;
            case TASK_LIFECYCLE_EVENT -> forwardTaskLifecycleEvents;
            case TRANSPORT_SIGNAL -> forwardTransportSignals;
            case HEARTBEAT_SIGNAL -> forwardHeartbeatSignals;
            case SYSTEM_SIGNAL -> forwardSystemSignals;
        };
    }

    private static String normalizePath(String value) {
        return value.startsWith("/") ? value : "/" + value;
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
