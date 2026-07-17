package com.opensocket.aievent.core.config;

import java.time.Duration;
import java.util.Set;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.decision")
public class CoreDecisionProperties {
    /** Same fingerprint within this window is treated as duplicate aggregation. */
    private Duration dedupWindow = Duration.ofMinutes(5);
    /** Dedup hot-state TTL. Should be longer than dedupWindow. */
    private Duration dedupTtl = Duration.ofHours(2);
    /** Incident active TTL for in-memory mode. */
    private Duration incidentTtl = Duration.ofHours(12);
    /** P1/P2 keeps task creation disabled by default. P3 may enable this via profile/env. */
    private boolean taskCreationEnabled = false;
    private boolean taskEscalationEnabled = true;
    private int taskMinOccurrences = 30;
    private Set<String> immediateTaskSeverities = Set.of("CRITICAL");
    private String defaultRoutingPolicy = "MANUAL_REVIEW";
    private List<String> defaultTaskCapabilities = List.of();
    /** P1 must never call MCP directly. */
    private boolean mcpActionEnabled = false;
    /** P1 must never create issue directly. */
    private boolean issueActionEnabled = false;

    public Duration getDedupWindow() { return dedupWindow; }
    public void setDedupWindow(Duration dedupWindow) { this.dedupWindow = dedupWindow; }
    public Duration getDedupTtl() { return dedupTtl; }
    public void setDedupTtl(Duration dedupTtl) { this.dedupTtl = dedupTtl; }
    public Duration getIncidentTtl() { return incidentTtl; }
    public void setIncidentTtl(Duration incidentTtl) { this.incidentTtl = incidentTtl; }
    public boolean isTaskCreationEnabled() { return taskCreationEnabled; }
    public void setTaskCreationEnabled(boolean taskCreationEnabled) { this.taskCreationEnabled = taskCreationEnabled; }
    public boolean isTaskEscalationEnabled() { return taskEscalationEnabled; }
    public void setTaskEscalationEnabled(boolean taskEscalationEnabled) { this.taskEscalationEnabled = taskEscalationEnabled; }
    public int getTaskMinOccurrences() { return taskMinOccurrences; }
    public void setTaskMinOccurrences(int taskMinOccurrences) { this.taskMinOccurrences = taskMinOccurrences; }
    public Set<String> getImmediateTaskSeverities() { return immediateTaskSeverities; }
    public void setImmediateTaskSeverities(Set<String> immediateTaskSeverities) { this.immediateTaskSeverities = immediateTaskSeverities; }
    public String getDefaultRoutingPolicy() { return defaultRoutingPolicy; }
    public void setDefaultRoutingPolicy(String defaultRoutingPolicy) { this.defaultRoutingPolicy = defaultRoutingPolicy; }
    public List<String> getDefaultTaskCapabilities() { return defaultTaskCapabilities; }
    public void setDefaultTaskCapabilities(List<String> defaultTaskCapabilities) { this.defaultTaskCapabilities = defaultTaskCapabilities; }
    public boolean isMcpActionEnabled() { return mcpActionEnabled; }
    public void setMcpActionEnabled(boolean mcpActionEnabled) { this.mcpActionEnabled = mcpActionEnabled; }
    public boolean isIssueActionEnabled() { return issueActionEnabled; }
    public void setIssueActionEnabled(boolean issueActionEnabled) { this.issueActionEnabled = issueActionEnabled; }
}
