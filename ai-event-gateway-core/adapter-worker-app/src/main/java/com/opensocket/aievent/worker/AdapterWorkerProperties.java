package com.opensocket.aievent.worker;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapter-worker")
public class AdapterWorkerProperties {
    private boolean enabled = true;
    private String coreBaseUrl = "http://localhost:18080";
    private String workerId = "adapter-worker-001";
    private Set<String> adapterTypes = new LinkedHashSet<>(Set.of("MCP", "ISSUE_TRACKING"));
    private long leaseSeconds = 120;
    private long pollIntervalMs = 2000;
    private Duration requestTimeout = Duration.ofSeconds(15);
    private String tokenHeader = "X-Internal-Token";
    private String token = "";
    private String mcpEndpointUrl = "";
    private String issueEndpointUrl = "";
    private boolean mockSuccessEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    public String getCoreBaseUrl() {
        return coreBaseUrl;
    }

    public void setCoreBaseUrl(String v) {
        coreBaseUrl = v;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String v) {
        workerId = v;
    }

    public Set<String> getAdapterTypes() {
        return adapterTypes;
    }

    public void setAdapterTypes(Set<String> v) {
        adapterTypes = v;
    }

    public long getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(long v) {
        leaseSeconds = Math.max(10, v);
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long v) {
        pollIntervalMs = Math.max(250, v);
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration v) {
        requestTimeout = v;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String v) {
        tokenHeader = v;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String v) {
        token = v;
    }

    public String getMcpEndpointUrl() {
        return mcpEndpointUrl;
    }

    public void setMcpEndpointUrl(String v) {
        mcpEndpointUrl = v == null ? "" : v;
    }

    public String getIssueEndpointUrl() {
        return issueEndpointUrl;
    }

    public void setIssueEndpointUrl(String v) {
        issueEndpointUrl = v == null ? "" : v;
    }

    public boolean isMockSuccessEnabled() {
        return mockSuccessEnabled;
    }

    public void setMockSuccessEnabled(boolean v) {
        mockSuccessEnabled = v;
    }

    public boolean isConfigured(String adapterType) {
        if (mockSuccessEnabled)
            return true;
        if ("MCP".equalsIgnoreCase(adapterType))
            return !mcpEndpointUrl.isBlank();
        if ("ISSUE_TRACKING".equalsIgnoreCase(adapterType))
            return !issueEndpointUrl.isBlank();
        return false;
    }
}
