package com.opensocket.aievent.core.dispatch;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dispatch")
public class DispatchProperties {
    private boolean requestCreationEnabled = true;
    private DispatchReviewMode reviewMode = DispatchReviewMode.NOT_REQUIRED;
    private String requestStore = "MEMORY";
    private String sourceNodeId = "ai-event-gateway-core";
    private String gatewayDispatchPath = "/internal/delivery/agents/{agentId}/commands";
    private boolean requireAssignableAgent = true;
    private String workerId = "core-dispatch";
    private Duration claimLease = Duration.ofSeconds(30);
    private DispatchExecutionPolicy executionPolicy = DispatchExecutionPolicy.AUTO_AFTER_ASSIGNMENT;
    private Client client = new Client();
    private Retry retry = new Retry();
    private FailureRequeue failureRequeue = new FailureRequeue();

    public boolean isRequestCreationEnabled() { return requestCreationEnabled; }
    public void setRequestCreationEnabled(boolean requestCreationEnabled) { this.requestCreationEnabled = requestCreationEnabled; }
    public DispatchReviewMode getReviewMode() { return reviewMode; }
    public void setReviewMode(DispatchReviewMode reviewMode) { this.reviewMode = reviewMode == null ? DispatchReviewMode.NOT_REQUIRED : reviewMode; }
    public String getRequestStore() { return requestStore; }
    public void setRequestStore(String requestStore) { this.requestStore = requestStore == null ? "MEMORY" : requestStore; }
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId == null || sourceNodeId.isBlank() ? "ai-event-gateway-core" : sourceNodeId; }
    public String getGatewayDispatchPath() { return gatewayDispatchPath; }
    public void setGatewayDispatchPath(String gatewayDispatchPath) { this.gatewayDispatchPath = gatewayDispatchPath == null || gatewayDispatchPath.isBlank() ? "/internal/delivery/agents/{agentId}/commands" : gatewayDispatchPath; }
    public boolean isRequireAssignableAgent() { return requireAssignableAgent; }
    public void setRequireAssignableAgent(boolean requireAssignableAgent) { this.requireAssignableAgent = requireAssignableAgent; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId == null || workerId.isBlank() ? "core-dispatch" : workerId.trim(); }
    public Duration getClaimLease() { return claimLease; }
    public void setClaimLease(Duration claimLease) { this.claimLease = claimLease == null || claimLease.isZero() || claimLease.isNegative() ? Duration.ofSeconds(30) : claimLease; }
    public DispatchExecutionPolicy getExecutionPolicy() { return executionPolicy; }
    public void setExecutionPolicy(DispatchExecutionPolicy executionPolicy) { this.executionPolicy = executionPolicy == null ? DispatchExecutionPolicy.AUTO_AFTER_ASSIGNMENT : executionPolicy; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client == null ? new Client() : client; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry == null ? new Retry() : retry; }
    public FailureRequeue getFailureRequeue() { return failureRequeue; }
    public void setFailureRequeue(FailureRequeue failureRequeue) { this.failureRequeue = failureRequeue == null ? new FailureRequeue() : failureRequeue; }

    public static class FailureRequeue {
        private boolean enabled = true;
        private int maxReassignments = 3;
        private Duration runtimeInitialBackoff = Duration.ofSeconds(30);
        private Duration runtimeMaxBackoff = Duration.ofMinutes(5);
        private int runtimeJitterPercent = 20;
        private int poisonAgentFailureThreshold = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxReassignments() { return maxReassignments; }
        public void setMaxReassignments(int maxReassignments) { this.maxReassignments = Math.max(0, Math.min(maxReassignments, 20)); }
        public Duration getRuntimeInitialBackoff() { return runtimeInitialBackoff; }
        public void setRuntimeInitialBackoff(Duration runtimeInitialBackoff) { this.runtimeInitialBackoff = runtimeInitialBackoff == null || runtimeInitialBackoff.isNegative() || runtimeInitialBackoff.isZero() ? Duration.ofSeconds(30) : runtimeInitialBackoff; }
        public Duration getRuntimeMaxBackoff() { return runtimeMaxBackoff; }
        public void setRuntimeMaxBackoff(Duration runtimeMaxBackoff) { this.runtimeMaxBackoff = runtimeMaxBackoff == null || runtimeMaxBackoff.isNegative() || runtimeMaxBackoff.isZero() ? Duration.ofMinutes(5) : runtimeMaxBackoff; }
        public int getRuntimeJitterPercent() { return runtimeJitterPercent; }
        public void setRuntimeJitterPercent(int runtimeJitterPercent) { this.runtimeJitterPercent = Math.max(0, Math.min(runtimeJitterPercent, 100)); }
        public int getPoisonAgentFailureThreshold() { return poisonAgentFailureThreshold; }
        public void setPoisonAgentFailureThreshold(int poisonAgentFailureThreshold) { this.poisonAgentFailureThreshold = Math.max(1, Math.min(poisonAgentFailureThreshold, 100)); }
    }

    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofSeconds(30);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private int jitterPercent = 20;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(1, Math.min(maxAttempts, 20)); }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff == null ? Duration.ofSeconds(30) : initialBackoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff == null ? Duration.ofMinutes(5) : maxBackoff; }
        public int getJitterPercent() { return jitterPercent; }
        public void setJitterPercent(int jitterPercent) { this.jitterPercent = Math.max(0, Math.min(jitterPercent, 100)); }
    }

    public static class Client {
        private boolean enabled = true;
        /**
         * Deprecated compatibility switch. Phase 1 uses dispatch.execution-policy instead.
         */
        private boolean autoExecuteApproved = true;
        private String defaultGatewayBaseUrl = "http://localhost:18081";
        private Map<String, String> gatewayBaseUrls = new LinkedHashMap<>();
        private String internalTokenHeader = "X-Cluster-Token";
        private String internalToken = "";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration requestTimeout = Duration.ofSeconds(10);
        private int maxBatchSize = 100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAutoExecuteApproved() { return autoExecuteApproved; }
        public void setAutoExecuteApproved(boolean autoExecuteApproved) { this.autoExecuteApproved = autoExecuteApproved; }
        public String getDefaultGatewayBaseUrl() { return defaultGatewayBaseUrl; }
        public void setDefaultGatewayBaseUrl(String defaultGatewayBaseUrl) { this.defaultGatewayBaseUrl = blank(defaultGatewayBaseUrl) ? "http://localhost:18081" : defaultGatewayBaseUrl; }
        public Map<String, String> getGatewayBaseUrls() { return gatewayBaseUrls; }
        public void setGatewayBaseUrls(Map<String, String> gatewayBaseUrls) { this.gatewayBaseUrls = gatewayBaseUrls == null ? new LinkedHashMap<>() : new LinkedHashMap<>(gatewayBaseUrls); }
        public String getInternalTokenHeader() { return internalTokenHeader; }
        public void setInternalTokenHeader(String internalTokenHeader) { this.internalTokenHeader = blank(internalTokenHeader) ? "X-Cluster-Token" : internalTokenHeader; }
        public String getInternalToken() { return internalToken; }
        public void setInternalToken(String internalToken) { this.internalToken = internalToken == null ? "" : internalToken; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout; }
        public int getMaxBatchSize() { return maxBatchSize; }
        public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = Math.max(1, Math.min(maxBatchSize, 1000)); }
        private boolean blank(String v) { return v == null || v.isBlank(); }
    }
}
