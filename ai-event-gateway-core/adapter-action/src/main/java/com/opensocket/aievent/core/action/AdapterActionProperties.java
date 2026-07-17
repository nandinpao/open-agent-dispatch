package com.opensocket.aievent.core.action;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapter-actions")
public class AdapterActionProperties {
    private String store = "MEMORY";
    private boolean createSuppressedRecords = true;
    private final Mcp mcp = new Mcp();
    private final Issue issue = new Issue();
    private final Worker worker = new Worker();

    public String getStore() { return store; }
    public void setStore(String store) { this.store = store == null ? "MEMORY" : store; }
    public boolean isCreateSuppressedRecords() { return createSuppressedRecords; }
    public void setCreateSuppressedRecords(boolean createSuppressedRecords) { this.createSuppressedRecords = createSuppressedRecords; }
    public Mcp getMcp() { return mcp; }
    public Issue getIssue() { return issue; }
    public Worker getWorker() { return worker; }

    public static class Worker {
        private boolean retryEnabled = true;
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofSeconds(30);
        private Duration maxBackoff = Duration.ofMinutes(10);
        private int expiredLeaseScanBatchSize = 100;

        public boolean isRetryEnabled() { return retryEnabled; }
        public void setRetryEnabled(boolean retryEnabled) { this.retryEnabled = retryEnabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(1, Math.min(maxAttempts, 20)); }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff == null ? Duration.ofSeconds(30) : initialBackoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff == null ? Duration.ofMinutes(10) : maxBackoff; }
        public int getExpiredLeaseScanBatchSize() { return expiredLeaseScanBatchSize; }
        public void setExpiredLeaseScanBatchSize(int expiredLeaseScanBatchSize) { this.expiredLeaseScanBatchSize = Math.max(1, Math.min(expiredLeaseScanBatchSize, 1000)); }
    }

    public static class Mcp {
        private boolean enabled = false;
        private boolean runOnCompletedTask = true;
        private boolean runOnFailedTask = false;
        private boolean onePerTask = true;
        private String adapterName = "mcp-default";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isRunOnCompletedTask() { return runOnCompletedTask; }
        public void setRunOnCompletedTask(boolean runOnCompletedTask) { this.runOnCompletedTask = runOnCompletedTask; }
        public boolean isRunOnFailedTask() { return runOnFailedTask; }
        public void setRunOnFailedTask(boolean runOnFailedTask) { this.runOnFailedTask = runOnFailedTask; }
        public boolean isOnePerTask() { return onePerTask; }
        public void setOnePerTask(boolean onePerTask) { this.onePerTask = onePerTask; }
        public String getAdapterName() { return adapterName; }
        public void setAdapterName(String adapterName) { this.adapterName = adapterName == null ? "mcp-default" : adapterName; }
    }

    public static class Issue {
        private boolean enabled = false;
        private boolean createOnCompletedTask = false;
        private boolean createOnFailedTask = true;
        private boolean updateExistingIssueComment = true;
        private boolean oneCreatePerIncident = true;
        private boolean oneUpdatePerTask = true;
        private String adapterName = "issue-default";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isCreateOnCompletedTask() { return createOnCompletedTask; }
        public void setCreateOnCompletedTask(boolean createOnCompletedTask) { this.createOnCompletedTask = createOnCompletedTask; }
        public boolean isCreateOnFailedTask() { return createOnFailedTask; }
        public void setCreateOnFailedTask(boolean createOnFailedTask) { this.createOnFailedTask = createOnFailedTask; }
        public boolean isUpdateExistingIssueComment() { return updateExistingIssueComment; }
        public void setUpdateExistingIssueComment(boolean updateExistingIssueComment) { this.updateExistingIssueComment = updateExistingIssueComment; }
        public boolean isOneCreatePerIncident() { return oneCreatePerIncident; }
        public void setOneCreatePerIncident(boolean oneCreatePerIncident) { this.oneCreatePerIncident = oneCreatePerIncident; }
        public boolean isOneUpdatePerTask() { return oneUpdatePerTask; }
        public void setOneUpdatePerTask(boolean oneUpdatePerTask) { this.oneUpdatePerTask = oneUpdatePerTask; }
        public String getAdapterName() { return adapterName; }
        public void setAdapterName(String adapterName) { this.adapterName = adapterName == null ? "issue-default" : adapterName; }
    }
}
