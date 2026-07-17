package com.opensocket.aievent.core.action.executor;

import java.util.ArrayList;
import java.util.List;

import com.opensocket.aievent.core.action.AdapterAction;

public class AdapterActionExecutionSummary {
    private int requested;
    private int executed;
    private int completed;
    private int failed;
    private int retryScheduled;
    private List<AdapterAction> actions = new ArrayList<>();

    public int getRequested() { return requested; }
    public void setRequested(int requested) { this.requested = requested; }
    public int getExecuted() { return executed; }
    public void setExecuted(int executed) { this.executed = executed; }
    public int getCompleted() { return completed; }
    public void setCompleted(int completed) { this.completed = completed; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public int getRetryScheduled() { return retryScheduled; }
    public void setRetryScheduled(int retryScheduled) { this.retryScheduled = retryScheduled; }
    public List<AdapterAction> getActions() { return actions; }
    public void setActions(List<AdapterAction> actions) { this.actions = actions == null ? new ArrayList<>() : new ArrayList<>(actions); }
}
