package com.opensocket.aievent.core.lifecycle;

public class LifecycleScanResult {
    private int scanned;
    private int updated;
    private int reassigned;
    private int timedOut;
    private String message;

    public static LifecycleScanResult empty(String message) {
        LifecycleScanResult result = new LifecycleScanResult();
        result.setMessage(message);
        return result;
    }

    public int getScanned() { return scanned; }
    public void setScanned(int scanned) { this.scanned = Math.max(0, scanned); }
    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = Math.max(0, updated); }
    public int getReassigned() { return reassigned; }
    public void setReassigned(int reassigned) { this.reassigned = Math.max(0, reassigned); }
    public int getTimedOut() { return timedOut; }
    public void setTimedOut(int timedOut) { this.timedOut = Math.max(0, timedOut); }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
