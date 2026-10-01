package com.opensocket.aievent.core.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Startup-only fallback binding retained until the V41 Configuration cutover is finalized. */
@ConfigurationProperties(prefix = "agent-remediation.workflow.stale-lease-reaper")
public class AgentRemediationWorkflowRuntimeProperties {
    private boolean enabled = true;
    private long fixedDelayMs = 60000;
    private long initialDelayMs = 30000;
    private int limit = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getFixedDelayMs() { return fixedDelayMs; }
    public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    public long getInitialDelayMs() { return initialDelayMs; }
    public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
}
