package com.opensocket.aievent.core.task;

public class TaskDispatchRecoveryScanResult {
    private int claimed;
    private int recovered;
    private int deferred;
    private int skipped;
    private int failed;
    private String message;

    public int getClaimed() { return claimed; }
    public void setClaimed(int claimed) { this.claimed = Math.max(0, claimed); }
    public int getRecovered() { return recovered; }
    public void setRecovered(int recovered) { this.recovered = Math.max(0, recovered); }
    public int getDeferred() { return deferred; }
    public void setDeferred(int deferred) { this.deferred = Math.max(0, deferred); }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = Math.max(0, skipped); }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = Math.max(0, failed); }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public static TaskDispatchRecoveryScanResult disabled(String message) {
        TaskDispatchRecoveryScanResult result = new TaskDispatchRecoveryScanResult();
        result.setMessage(message);
        return result;
    }
}
