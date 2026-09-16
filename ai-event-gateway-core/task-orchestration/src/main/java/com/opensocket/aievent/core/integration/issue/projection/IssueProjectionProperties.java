package com.opensocket.aievent.core.integration.issue.projection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "issue-projection")
public class IssueProjectionProperties {
    private boolean enabled = true;
    private int maxAttempts = 8;
    private int reconcileBatchSize = 100;
    private long retryDelaySeconds = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(1, maxAttempts); }
    public int getReconcileBatchSize() { return reconcileBatchSize; }
    public void setReconcileBatchSize(int value) { this.reconcileBatchSize = Math.max(1, value); }
    public long getRetryDelaySeconds() { return retryDelaySeconds; }
    public void setRetryDelaySeconds(long value) { this.retryDelaySeconds = Math.max(1, value); }
}
