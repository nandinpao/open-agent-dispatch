package com.opensocket.aievent.core.task;

import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.decision")
public class TaskOrchestrationProperties {
    private boolean taskCreationEnabled = false;
    private boolean taskEscalationEnabled = true;
    private int taskMinOccurrences = 30;
    private Set<String> immediateTaskSeverities = Set.of("CRITICAL");
    private String defaultRoutingPolicy = "MANUAL_REVIEW";
    private List<String> defaultTaskCapabilities = List.of();

    public boolean isTaskCreationEnabled() { return taskCreationEnabled; }
    public void setTaskCreationEnabled(boolean taskCreationEnabled) { this.taskCreationEnabled = taskCreationEnabled; }
    public boolean isTaskEscalationEnabled() { return taskEscalationEnabled; }
    public void setTaskEscalationEnabled(boolean taskEscalationEnabled) { this.taskEscalationEnabled = taskEscalationEnabled; }
    public int getTaskMinOccurrences() { return taskMinOccurrences; }
    public void setTaskMinOccurrences(int taskMinOccurrences) { this.taskMinOccurrences = Math.max(1, taskMinOccurrences); }
    public Set<String> getImmediateTaskSeverities() { return immediateTaskSeverities; }
    public void setImmediateTaskSeverities(Set<String> immediateTaskSeverities) { this.immediateTaskSeverities = immediateTaskSeverities == null ? Set.of() : Set.copyOf(immediateTaskSeverities); }
    public String getDefaultRoutingPolicy() { return defaultRoutingPolicy; }
    public void setDefaultRoutingPolicy(String defaultRoutingPolicy) { this.defaultRoutingPolicy = defaultRoutingPolicy; }
    public List<String> getDefaultTaskCapabilities() { return defaultTaskCapabilities; }
    public void setDefaultTaskCapabilities(List<String> defaultTaskCapabilities) { this.defaultTaskCapabilities = defaultTaskCapabilities == null ? List.of() : List.copyOf(defaultTaskCapabilities); }
}
