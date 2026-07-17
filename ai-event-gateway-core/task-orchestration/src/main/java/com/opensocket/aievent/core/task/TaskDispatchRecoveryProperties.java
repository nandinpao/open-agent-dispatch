package com.opensocket.aievent.core.task;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Task-level delayed dispatch recovery policy. This is intentionally distinct from
 * dispatch-request retry: it is used when no assignment can be created because no
 * effective runtime candidate is currently available.
 */
@ConfigurationProperties(prefix = "task.dispatch-recovery")
public class TaskDispatchRecoveryProperties {
    private boolean enabled = true;
    private boolean scannerEnabled = true;
    private int maxBatchSize = 50;
    /** 0 means unlimited; lifecycle timeout policies may still terminate the task. */
    private int maxAttempts = 0;
    private Duration initialDelay = Duration.ofSeconds(30);
    private Duration maxDelay = Duration.ofMinutes(10);
    private Duration claimLease = Duration.ofSeconds(30);
    private String workerId = "core-task-dispatch-recovery";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isScannerEnabled() { return scannerEnabled; }
    public void setScannerEnabled(boolean scannerEnabled) { this.scannerEnabled = scannerEnabled; }
    public int getMaxBatchSize() { return maxBatchSize; }
    public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = Math.max(1, Math.min(maxBatchSize, 1000)); }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(0, Math.min(maxAttempts, 1000)); }
    public Duration getInitialDelay() { return initialDelay; }
    public void setInitialDelay(Duration initialDelay) { this.initialDelay = sane(initialDelay, Duration.ofSeconds(30)); }
    public Duration getMaxDelay() { return maxDelay; }
    public void setMaxDelay(Duration maxDelay) { this.maxDelay = sane(maxDelay, Duration.ofMinutes(10)); }
    public Duration getClaimLease() { return claimLease; }
    public void setClaimLease(Duration claimLease) { this.claimLease = sane(claimLease, Duration.ofSeconds(30)); }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId == null || workerId.isBlank() ? "core-task-dispatch-recovery" : workerId.trim(); }

    public Duration delayForAttempt(int attemptNo) {
        int attempt = Math.max(1, attemptNo);
        long multiplier = 1L << Math.max(0, Math.min(attempt - 1, 10));
        Duration candidate = initialDelay.multipliedBy(multiplier);
        return candidate.compareTo(maxDelay) > 0 ? maxDelay : candidate;
    }

    private Duration sane(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
