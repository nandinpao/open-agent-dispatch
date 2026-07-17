package com.opensocket.aievent.core.lifecycle;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.lifecycle")
public class LifecycleProperties {
    private Incident incident = new Incident();
    private Task task = new Task();

    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident == null ? new Incident() : incident; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task == null ? new Task() : task; }

    public enum ReopenPolicy {
        CREATE_NEW,
        REOPEN_RECENT
    }

    public static class Incident {
        private boolean autoResolveEnabled = true;
        private long scanIntervalMs = 60000;
        private Duration inactiveThreshold = Duration.ofHours(12);
        private int maxBatchSize = 100;
        private ReopenPolicy reopenPolicy = ReopenPolicy.REOPEN_RECENT;
        private Duration reopenWindow = Duration.ofHours(24);

        public boolean isAutoResolveEnabled() { return autoResolveEnabled; }
        public void setAutoResolveEnabled(boolean autoResolveEnabled) { this.autoResolveEnabled = autoResolveEnabled; }
        public long getScanIntervalMs() { return scanIntervalMs; }
        public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = Math.max(1000, scanIntervalMs); }
        public Duration getInactiveThreshold() { return inactiveThreshold; }
        public void setInactiveThreshold(Duration inactiveThreshold) { this.inactiveThreshold = inactiveThreshold == null ? Duration.ofHours(12) : inactiveThreshold; }
        public int getMaxBatchSize() { return maxBatchSize; }
        public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = Math.max(1, Math.min(maxBatchSize, 1000)); }
        public ReopenPolicy getReopenPolicy() { return reopenPolicy; }
        public void setReopenPolicy(ReopenPolicy reopenPolicy) { this.reopenPolicy = reopenPolicy == null ? ReopenPolicy.REOPEN_RECENT : reopenPolicy; }
        public Duration getReopenWindow() { return reopenWindow; }
        public void setReopenWindow(Duration reopenWindow) { this.reopenWindow = reopenWindow == null ? Duration.ofHours(24) : reopenWindow; }
    }

    public static class Task {
        private boolean timeoutEnabled = true;
        private boolean autoReassignEnabled = true;
        private long scanIntervalMs = 30000;
        private Duration createdTimeout = Duration.ofMinutes(10);
        private Duration assignedTimeout = Duration.ofMinutes(10);
        private Duration dispatchedTimeout = Duration.ofMinutes(10);
        private Duration runningTimeout = Duration.ofMinutes(30);
        private int maxReassignments = 2;
        private int maxBatchSize = 100;

        public boolean isTimeoutEnabled() { return timeoutEnabled; }
        public void setTimeoutEnabled(boolean timeoutEnabled) { this.timeoutEnabled = timeoutEnabled; }
        public boolean isAutoReassignEnabled() { return autoReassignEnabled; }
        public void setAutoReassignEnabled(boolean autoReassignEnabled) { this.autoReassignEnabled = autoReassignEnabled; }
        public long getScanIntervalMs() { return scanIntervalMs; }
        public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = Math.max(1000, scanIntervalMs); }
        public Duration getCreatedTimeout() { return createdTimeout; }
        public void setCreatedTimeout(Duration createdTimeout) { this.createdTimeout = createdTimeout == null ? Duration.ofMinutes(10) : createdTimeout; }
        public Duration getAssignedTimeout() { return assignedTimeout; }
        public void setAssignedTimeout(Duration assignedTimeout) { this.assignedTimeout = assignedTimeout == null ? Duration.ofMinutes(10) : assignedTimeout; }
        public Duration getDispatchedTimeout() { return dispatchedTimeout; }
        public void setDispatchedTimeout(Duration dispatchedTimeout) { this.dispatchedTimeout = dispatchedTimeout == null ? Duration.ofMinutes(10) : dispatchedTimeout; }
        public Duration getRunningTimeout() { return runningTimeout; }
        public void setRunningTimeout(Duration runningTimeout) { this.runningTimeout = runningTimeout == null ? Duration.ofMinutes(30) : runningTimeout; }
        public int getMaxReassignments() { return maxReassignments; }
        public void setMaxReassignments(int maxReassignments) { this.maxReassignments = Math.max(0, maxReassignments); }
        public int getMaxBatchSize() { return maxBatchSize; }
        public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = Math.max(1, Math.min(maxBatchSize, 1000)); }
    }
}
