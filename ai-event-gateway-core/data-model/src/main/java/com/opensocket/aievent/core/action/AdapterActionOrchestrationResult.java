package com.opensocket.aievent.core.action;

import java.util.List;

public class AdapterActionOrchestrationResult {
    private String taskId;
    private String incidentId;
    private boolean terminalTask;
    private boolean mcpActionCreated;
    private boolean issueActionCreated;
    private int createdCount;
    private int suppressedCount;
    private List<AdapterAction> actions = List.of();

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public boolean isTerminalTask() { return terminalTask; }
    public void setTerminalTask(boolean terminalTask) { this.terminalTask = terminalTask; }
    public boolean isMcpActionCreated() { return mcpActionCreated; }
    public void setMcpActionCreated(boolean mcpActionCreated) { this.mcpActionCreated = mcpActionCreated; }
    public boolean isIssueActionCreated() { return issueActionCreated; }
    public void setIssueActionCreated(boolean issueActionCreated) { this.issueActionCreated = issueActionCreated; }
    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }
    public int getSuppressedCount() { return suppressedCount; }
    public void setSuppressedCount(int suppressedCount) { this.suppressedCount = suppressedCount; }
    public List<AdapterAction> getActions() { return actions; }
    public void setActions(List<AdapterAction> actions) { this.actions = actions == null ? List.of() : List.copyOf(actions); }
}
