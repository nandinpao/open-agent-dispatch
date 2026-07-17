package com.opensocket.aievent.core.action.executor;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapter-executor")
public class AdapterActionExecutionProperties {
    private String mode = "disabled";
    private boolean enabled = false;
    private boolean autoExecutePending = false;
    private Duration autoExecuteInterval = Duration.ofSeconds(10);
    private int batchSize = 50;
    private int maxAttempts = 3;
    private Duration initialBackoff = Duration.ofSeconds(30);
    private Duration maxBackoff = Duration.ofMinutes(10);
    private Duration executionTimeout = Duration.ofSeconds(30);
    private boolean markUnavailableWhenNoExecutor = true;
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final Mock mock = new Mock();
    private final Mcp mcp = new Mcp();
    private final Issue issue = new Issue();
    private final Audit audit = new Audit();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode == null || mode.isBlank() ? "disabled" : mode.trim().toLowerCase(); }
    public boolean isEmbeddedMode() { return "embedded".equalsIgnoreCase(mode); }
    public boolean isExternalMode() { return "external".equalsIgnoreCase(mode); }
    public boolean isDisabledMode() { return "disabled".equalsIgnoreCase(mode); }
    public boolean isEnabled() { return enabled && isEmbeddedMode(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isAutoExecutePending() { return autoExecutePending; }
    public void setAutoExecutePending(boolean autoExecutePending) { this.autoExecutePending = autoExecutePending; }
    public Duration getAutoExecuteInterval() { return autoExecuteInterval; }
    public void setAutoExecuteInterval(Duration autoExecuteInterval) { this.autoExecuteInterval = autoExecuteInterval == null ? Duration.ofSeconds(10) : autoExecuteInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = Math.max(1, Math.min(batchSize, 1000)); }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(1, maxAttempts); }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff == null ? Duration.ofSeconds(30) : initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff == null ? Duration.ofMinutes(10) : maxBackoff; }
    public Duration getExecutionTimeout() { return executionTimeout; }
    public void setExecutionTimeout(Duration executionTimeout) { this.executionTimeout = AdapterSecretRedactor.safeHttpTimeout(executionTimeout, Duration.ofSeconds(30)); }
    public boolean isMarkUnavailableWhenNoExecutor() { return markUnavailableWhenNoExecutor; }
    public void setMarkUnavailableWhenNoExecutor(boolean markUnavailableWhenNoExecutor) { this.markUnavailableWhenNoExecutor = markUnavailableWhenNoExecutor; }
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public Mock getMock() { return mock; }
    public Mcp getMcp() { return mcp; }
    public Issue getIssue() { return issue; }
    public Audit getAudit() { return audit; }

    public static class CircuitBreaker {
        private boolean enabled = true;
        private int failureThreshold = 5;
        private Duration openDuration = Duration.ofMinutes(1);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = Math.max(1, failureThreshold); }
        public Duration getOpenDuration() { return openDuration; }
        public void setOpenDuration(Duration openDuration) { this.openDuration = openDuration == null ? Duration.ofMinutes(1) : openDuration; }
    }

    public static class Mock {
        private boolean enabled = false;
        private boolean forceFailure = false;
        private String mcpExecutorName = "mock-mcp-executor";
        private String issueExecutorName = "mock-issue-executor";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isForceFailure() { return forceFailure; }
        public void setForceFailure(boolean forceFailure) { this.forceFailure = forceFailure; }
        public String getMcpExecutorName() { return mcpExecutorName; }
        public void setMcpExecutorName(String mcpExecutorName) { this.mcpExecutorName = mcpExecutorName == null ? "mock-mcp-executor" : mcpExecutorName; }
        public String getIssueExecutorName() { return issueExecutorName; }
        public void setIssueExecutorName(String issueExecutorName) { this.issueExecutorName = issueExecutorName == null ? "mock-issue-executor" : issueExecutorName; }
    }

    public static class Mcp {
        private boolean httpEnabled = false;
        private String executorName = "mcp-http-executor";
        private String endpointUrl = "";
        private String bearerToken = "";
        private Duration timeout = Duration.ofSeconds(30);
        private boolean mockCompatible = false;

        public boolean isHttpEnabled() { return httpEnabled; }
        public void setHttpEnabled(boolean httpEnabled) { this.httpEnabled = httpEnabled; }
        public String getExecutorName() { return executorName; }
        public void setExecutorName(String executorName) { this.executorName = executorName == null ? "mcp-http-executor" : executorName; }
        public String getEndpointUrl() { return endpointUrl; }
        public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl == null ? "" : endpointUrl; }
        public String getBearerToken() { return bearerToken; }
        public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken == null ? "" : bearerToken; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = AdapterSecretRedactor.safeHttpTimeout(timeout, Duration.ofSeconds(30)); }
        public boolean isMockCompatible() { return mockCompatible; }
        public void setMockCompatible(boolean mockCompatible) { this.mockCompatible = mockCompatible; }
    }

    public static class Issue {
        private String defaultVendor = "";
        private boolean jiraMockEnabled = false;
        private boolean redmineMockEnabled = false;
        private boolean gitlabMockEnabled = false;
        private String jiraExecutorName = "jira-issue-executor";
        private String redmineExecutorName = "redmine-issue-executor";
        private String gitlabExecutorName = "gitlab-issue-executor";
        private final Redmine redmine = new Redmine();
        private final Gitlab gitlab = new Gitlab();

        public String getDefaultVendor() { return defaultVendor; }
        public void setDefaultVendor(String defaultVendor) { this.defaultVendor = defaultVendor == null ? "" : defaultVendor.trim(); }
        public boolean isJiraMockEnabled() { return jiraMockEnabled; }
        public void setJiraMockEnabled(boolean jiraMockEnabled) { this.jiraMockEnabled = jiraMockEnabled; }
        public boolean isRedmineMockEnabled() { return redmineMockEnabled; }
        public void setRedmineMockEnabled(boolean redmineMockEnabled) { this.redmineMockEnabled = redmineMockEnabled; }
        public boolean isGitlabMockEnabled() { return gitlabMockEnabled; }
        public void setGitlabMockEnabled(boolean gitlabMockEnabled) { this.gitlabMockEnabled = gitlabMockEnabled; }
        public String getJiraExecutorName() { return jiraExecutorName; }
        public void setJiraExecutorName(String jiraExecutorName) { this.jiraExecutorName = jiraExecutorName == null ? "jira-issue-executor" : jiraExecutorName; }
        public String getRedmineExecutorName() { return redmineExecutorName; }
        public void setRedmineExecutorName(String redmineExecutorName) { this.redmineExecutorName = redmineExecutorName == null ? "redmine-issue-executor" : redmineExecutorName; }
        public String getGitlabExecutorName() { return gitlabExecutorName; }
        public void setGitlabExecutorName(String gitlabExecutorName) { this.gitlabExecutorName = gitlabExecutorName == null ? "gitlab-issue-executor" : gitlabExecutorName; }
        public Redmine getRedmine() { return redmine; }
        public Gitlab getGitlab() { return gitlab; }
    }

    public static class Redmine {
        private boolean enabled = false;
        private String baseUrl = "";
        private String apiKey = "";
        private String projectId = "";
        private String trackerId = "";
        private String issueUrlTemplate = "";
        private String priorityCritical = "CRITICAL";
        private String priorityHigh = "HIGH";
        private String priorityMedium = "MIDDLE";
        private String priorityLow = "LOW";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl.trim(); }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey.trim(); }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId == null ? "" : projectId.trim(); }
        public String getTrackerId() { return trackerId; }
        public void setTrackerId(String trackerId) { this.trackerId = trackerId == null ? "" : trackerId.trim(); }
        public String getIssueUrlTemplate() { return issueUrlTemplate; }
        public void setIssueUrlTemplate(String issueUrlTemplate) { this.issueUrlTemplate = issueUrlTemplate == null ? "" : issueUrlTemplate.trim(); }
        public String getPriorityCritical() { return priorityCritical; }
        public void setPriorityCritical(String priorityCritical) { this.priorityCritical = priorityCritical == null ? "" : priorityCritical.trim(); }
        public String getPriorityHigh() { return priorityHigh; }
        public void setPriorityHigh(String priorityHigh) { this.priorityHigh = priorityHigh == null ? "" : priorityHigh.trim(); }
        public String getPriorityMedium() { return priorityMedium; }
        public void setPriorityMedium(String priorityMedium) { this.priorityMedium = priorityMedium == null ? "" : priorityMedium.trim(); }
        public String getPriorityLow() { return priorityLow; }
        public void setPriorityLow(String priorityLow) { this.priorityLow = priorityLow == null ? "" : priorityLow.trim(); }
    }

    public static class Gitlab {
        private boolean enabled = false;
        private String baseUrl = "";
        private String privateToken = "";
        private String projectId = "";
        private String issueUrlTemplate = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl.trim(); }
        public String getPrivateToken() { return privateToken; }
        public void setPrivateToken(String privateToken) { this.privateToken = privateToken == null ? "" : privateToken.trim(); }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId == null ? "" : projectId.trim(); }
        public String getIssueUrlTemplate() { return issueUrlTemplate; }
        public void setIssueUrlTemplate(String issueUrlTemplate) { this.issueUrlTemplate = issueUrlTemplate == null ? "" : issueUrlTemplate.trim(); }
    }

    public static class Audit {
        private String store = "MEMORY";
        private boolean payloadSnapshotEnabled = true;

        public String getStore() { return store; }
        public void setStore(String store) { this.store = store == null ? "MEMORY" : store; }
        public boolean isPayloadSnapshotEnabled() { return payloadSnapshotEnabled; }
        public void setPayloadSnapshotEnabled(boolean payloadSnapshotEnabled) { this.payloadSnapshotEnabled = payloadSnapshotEnabled; }
    }
}
